/* 
 * Copyright (c) 2014, 2015, 2016, 2017 dynatrace and/or its affiliates. All rights reserved.
 * This file is part of the AntTracks extension for the Hotspot VM. 
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License
 * along with with this work.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * File:   QuyteTreeCompression.cpp
 * Author: Philipp Lengauer (original code by Günther Blaschek)
 * 
 * Created on April 11, 2015, 10:49 AM
 */

#include "precompiled.hpp"
#include "QuyteTreeCompression.hpp"
#include "LZWDecompression.hpp"
#include "VarInt.hpp"

typedef jushort UInt16;
typedef jubyte UInt8;

typedef struct {
	UInt16 nodeID;
	UInt16 children[4];
} QuyteNode;

size_t requiredStorageForBufferSize(size_t bufferSize) {
	// We start with 256 preallocated nodes, each with a capacity of 4 successors.
	size_t maximumNodes = 256;
	// The first 1024 input bytes are most expensive when they are unique (no duplicates) and when
	// the first two bits are different for each prefix. Every character then results in 4 nodes, and up to
	// 4 characters can be attached to each of the 256 initial nodes.
	// example: 00-00-00-00, 01-00-00-00, 10-00-00-00, 11-00-00-00
	if (bufferSize <= 1024) {
		maximumNodes += bufferSize*4; // 4 nodes per input byte
		bufferSize = 0;
	} else {
		maximumNodes += 1024*4; // 4 nodes for each of the first 1024 input bytes
		bufferSize -= 1024;
	}
	// if the input buffer contains at most 1024 bytes, we are done.
	if (bufferSize > 0) {
		// If we have more than 1024 bytes, the rest must be attached to the 4-element chains.
		// Again, this is most expensive if we do no have any duplicates.
		// When each of the 256 prefix bytes occurs once, followed by each of the first bits 00, 01, 10, 11,
		// then up to three additional nodes can be atttached to the remaining three slots of the first quyte node.
		// This means that the next 1024*3 bytes require at most 3 nodes each.
		// example for 00-00-00-00: Attach chains 01-00-00, 10-00-00, 11-00-00 to the first node
		if (bufferSize <= 1024*3) {
			maximumNodes += bufferSize*3; // 3 nodes per input byte
			bufferSize = 0;
		} else {
			maximumNodes += 1024*3*4; // 4 nodes for each of the 1024*3 input bytes
			bufferSize -= 1024*3;
		}
	}
	// if the input buffer contains at most 1024*4 = 4096 bytes, we are done.
	// Otherwise, every subsequent byte requires at most 2 nodes.
	// Possibility 1, without duplicates:
	//	Without duplicates, the most expensive was to add a byte is by adding two-node chains
	//	after the first two existing nodes. There are 4096*3 = 16384 possibilities to do this.
	//	Every other addition without duplicates then adds only a single node.
	// Possibility 2, with duplicates:
	//	Adding a duplicate character (actually, a pair that already occurred earlier) is most expensive
	//	when 4 nodes are added to a leaf of the current tree. But in this case, 4 nodes are added for
	//	2 input bytes, so we also create at most 4 2 nodes per byte.
	maximumNodes += bufferSize*2;
	
	// Pessimistic estimation for more than 4096 input bytes:
	// nodes = 256 + 1024*4 + 1024*3*4 + (bytes-4096)*2 = 16640 + (bytes-4096)*2 = 8448 + bytes*2
	// We need to address the nodes with 16-bit indexes, which means that we can handle at most 65536 nodes.
	// In other words, 28544 input bytes is a conservative upper limit for the input buffer size.
	// We may be able to handle more when the input contains many duplicates, but the devil never sleeps...

	return maximumNodes * sizeof(QuyteNode);
}

QuyteTreeCompression::QuyteTreeCompression() : tempStorage(NULL), tempStorageLength(0) {}

QuyteTreeCompression::~QuyteTreeCompression() {
    if(tempStorage != NULL) free(tempStorage);
    tempStorage = NULL;
    tempStorageLength = 0;
}

void QuyteTreeCompression::reset() {
    //nothing to do
}

Compression::Result QuyteTreeCompression::encode(unsigned char* inputBuffer, size_t inputLength, unsigned char* outputBuffer, size_t outputLength) {
    guarantee(inputBuffer != NULL, "just checking");
    if(tempStorageLength < inputLength) {
        size_t new_length = requiredStorageForBufferSize(inputLength);
        tempStorage = realloc(tempStorage, new_length);
        memset(tempStorage, 0, new_length);
        tempStorageLength = inputLength;
    }
#ifdef DEBUG_COMPRESSION
    debug_print_array("uncompressed", inputBuffer, inputLength, 0);
    debug_print_array("  compressed", outputBuffer, 0, 0);
#endif

        VarIntWriter writer = VarIntWriter();
	UInt8 *inputCursor = inputBuffer;
	UInt8 *endOfInput = inputBuffer + inputLength;
        UInt8 *endOfOutput = outputBuffer + outputLength;
	QuyteNode *nodeArray = (QuyteNode*) tempStorage;
	int nodeCount = 256;
	int nextID = 256; // 0-255 are used for single bytes; 256 is the first new ID
	UInt8 *outputCursor = outputBuffer;
	// start with the first byte
        guarantee(inputCursor != NULL, "just checking again");
	UInt8 byte = *inputCursor;
	UInt16 lastMatchID = byte; // until we find a longer match, use the byte as the ID
	QuyteNode *currentNode = nodeArray + byte;
	inputCursor++; // advance to the second byte
	while (inputCursor < endOfInput && outputCursor + INDEX_MAX_WIDTH * 2 < endOfOutput) {
		byte = *inputCursor;
		UInt8 quyte;
		UInt16 nextNodeID;
		// find or create the QuyteNodes for the next byte; 2 bits per step, from MSB to LSB
		// XXoooooo
		quyte = byte >> 6;
		nextNodeID = currentNode->children[quyte];
		if (nextNodeID == 0) { // create new node
			nextNodeID = nodeCount++;
			currentNode->children[quyte] = nextNodeID;
		}
		currentNode = nodeArray + nextNodeID;
		// ooXXoooo
		quyte = (byte >> 4) & 0x3;
		nextNodeID = currentNode->children[quyte];
		if (nextNodeID == 0) { // create new node
			nextNodeID = nodeCount++;
			currentNode->children[quyte] = nextNodeID;
		}
		currentNode = nodeArray + nextNodeID;
		// ooooXXoo
		quyte = (byte >> 2) & 0x3;
		nextNodeID = currentNode->children[quyte];
		if (nextNodeID == 0) { // create new node
			nextNodeID = nodeCount++;
			currentNode->children[quyte] = nextNodeID;
		}
		currentNode = nodeArray + nextNodeID;
		// ooooooXX
		quyte = byte & 0x3;
		nextNodeID = currentNode->children[quyte];
		if (nextNodeID == 0) { // create new node
			nextNodeID = nodeCount++;
			currentNode->children[quyte] = nextNodeID;
			currentNode = nodeArray + nextNodeID;
			// a new leaf node was created => give it a new ID and emit lastMatchID from the previous iteration
			currentNode->nodeID = nextID++;
                        //outputCursor += VarInt::write(outputCursor, lastMatchID);
                        outputCursor += writer.write(outputCursor, lastMatchID);
#ifdef DEBUG_COMPRESSION
        debug_print_array("uncompressed", inputBuffer, inputLength, inputCursor - inputBuffer);
        debug_print_array("  compressed", outputBuffer, outputCursor - outputBuffer, outputCursor - outputBuffer);
#endif		
			// leave the input cursor where it is; start a new match with the current byte
			lastMatchID = byte; // use the byte as the ID
			currentNode = nodeArray + byte;
			inputCursor++; // advance to the next byte
		} else {
			currentNode = nodeArray + nextNodeID;
			// the node already existed, update lastMatchID and continue
			lastMatchID = currentNode->nodeID;
			inputCursor++; // advance to next byte
		}
	}
	// at the end, we need to emit the ID of the last match
        if (lastMatchID != 0xFFFF) {
            //outputCursor += VarInt::write(outputCursor, lastMatchID);
            if(outputCursor + INDEX_MAX_WIDTH * 2 < endOfOutput) outputCursor += writer.write(outputCursor, lastMatchID);
            else inputCursor = inputBuffer;
#ifdef DEBUG_COMPRESSION
        debug_print_array("uncompressed", inputBuffer, inputLength, inputCursor - inputBuffer);
        debug_print_array("  compressed", outputBuffer, outputCursor - outputBuffer, outputCursor - outputBuffer);
#endif		
        }
        memset(tempStorage, 0, nodeCount * sizeof(QuyteNode)); // clear the temporary storage again in preparation for the next run
        if(outputCursor + INDEX_MAX_WIDTH * 2 < endOfOutput) outputCursor += writer.flush(outputCursor);
        else inputCursor = inputBuffer;
        int length = (int)(outputCursor - outputBuffer);
        Compression::Result result;
        result.dest_length = length;
        result.src_compressed_length = inputCursor - inputBuffer;
        return result;
}

Decompression* QuyteTreeCompression::getDecompressor() {
    return new LZWDecompression();
}

QuyteTreeDecompression::QuyteTreeDecompression() : tempStorage(NULL), tempStorageLength(0) {}

QuyteTreeDecompression::~QuyteTreeDecompression() {
    free(tempStorage);
    tempStorage = NULL;
    tempStorageLength = 0;
}

void QuyteTreeDecompression::reset() {
    //nothing to do
}

typedef struct {
	UInt16 prefixID;
	UInt8 lastByte;
	UInt8 filler;
} SequenceElement;

UInt8* expandSequence(UInt8* outputCursor, int sequenceID, SequenceElement *sequenceStorage) {
	SequenceElement *element = sequenceStorage + sequenceID;
	if (element->prefixID <= 255) { // start of the chain, single byte
		*(outputCursor++) = element->prefixID;
	} else { // recursively add the prefix
		outputCursor = expandSequence(outputCursor, element->prefixID, sequenceStorage);
	}
	*(outputCursor++) = element->lastByte;
	return outputCursor;
}

size_t QuyteTreeDecompression::decode(unsigned char* inputBuffer, size_t inputLength, unsigned char* outputBuffer, size_t outputLength) {
    if(tempStorageLength < outputLength) {
        size_t new_length = requiredStorageForBufferSize(outputLength);
        tempStorage = realloc(tempStorage, new_length);
        memset(tempStorage, 0, new_length);
        tempStorageLength = outputLength;
    }
    
    	SequenceElement *sequenceStorage = (SequenceElement*) tempStorage;
	SequenceElement *nextSequenceElement = sequenceStorage + 256;
	UInt8 *inputCursor = inputBuffer;
	UInt8 *endOfInput = inputBuffer + inputLength;
	UInt8 *outputCursor = outputBuffer;
	int previousID = -1;
	UInt8 successorByte = 0;
        VarIntReader reader = VarIntReader();
	while (inputCursor < endOfInput) {
		UInt16 patternID;
                inputCursor +=  reader.read(inputCursor, (uint*) &patternID);
		if (patternID <= 255) { // the ID represents a single byte
			*(outputCursor++) = patternID;
			successorByte = patternID;
		} else { // the ID represents a longer sequence
			UInt8 *addressOfFirstSequenceByte = outputCursor;
			if (patternID < (nextSequenceElement-sequenceStorage)) { // complete pattern
				outputCursor = expandSequence(outputCursor, patternID, sequenceStorage);
			} else { // emit last output plus first character of last output
				if (previousID <= 255) { // single byte; output twice
					*(outputCursor++) = previousID;
					*(outputCursor++) = previousID;
				} else {
					outputCursor = expandSequence(outputCursor, previousID, sequenceStorage);
					*(outputCursor++) = *addressOfFirstSequenceByte;
				}
			}
			successorByte = *addressOfFirstSequenceByte;
		}
		if (previousID >= 0) {
			nextSequenceElement->prefixID = previousID;
			nextSequenceElement->lastByte = successorByte;
			nextSequenceElement++;
		}
		previousID = patternID;
	}
	// clear the temporary storage
	memset(tempStorage, 0, (UInt8*)(nextSequenceElement) - (UInt8*)(tempStorage));
	return (size_t)(outputCursor - outputBuffer);
}
