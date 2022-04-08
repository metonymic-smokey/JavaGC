/*
 * Copyright 2014 Edward Aftandilian. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/*
 * Copyright 2014 Edward Aftandilian. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.jku.anttracks.parser.hprof;

import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.parser.hprof.datastructures.FileHandler;
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        HprofParser parser = new HprofParser(new HprofToFastHeapHandler(), null);

        try {
            parser.parse(new File(".\\src\\test\\resources\\java.hprof"));
        } catch (IOException e) {
            System.err.println(e);
        }

        HprofToFastHeapHandler h = (HprofToFastHeapHandler) parser.getHandler();
        print(h);

    }

    private static void print(HprofToFastHeapHandler h) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < h.getAddr().length; i++) {
            sb.append(h.getAddr()[i] + "\n");
            //to
            sb.append("to: [");
            for (long l : h.getToPtrs()[i]) {
                sb.append(l + ", ");
            }
            sb.append("]\n");
            //from
            sb.append("from: [");
            for (long l : h.getFrmPtrs()[i]) {
                sb.append(l + ", ");
            }
            sb.append("]\n");
            //type
            sb.append("Type: " + h.getClassMap().get(h.getClassKeys()[i]).externalClassName + "\n");
            //root
            sb.append("Root: [");
            if (h.getGcRoots().get(i) != null) {
                for (RootPtr r : h.getGcRoots().get(i)) {
                    if (r != null) {
                        sb.append(r.toString() + ", ");
                    }
                }
            }
            sb.append("]\n");
        }

        try {
//		System.out.println(sb.toString());
            FileHandler.writeAllLines(".\\src\\test\\resources\\output.txt", sb.toString());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
