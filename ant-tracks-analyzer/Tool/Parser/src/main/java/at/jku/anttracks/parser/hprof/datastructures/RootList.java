package at.jku.anttracks.parser.hprof.datastructures;

import java.util.ArrayList;

public class RootList {
	private RootList next;
	private RootPtr value;

	public RootList(RootPtr value) {
		this.value = value;
	}

	public RootList getNext() {
		return next;
	}

	public void setNext(RootList next) {
		this.next = next;
	}

	public RootPtr getValue() {
		return value;
	}

	public RootPtr[] toArray() {
		ArrayList<RootPtr> list = new ArrayList<>();
		RootList cur = this;
		do {
			list.add(cur.value);
			cur = cur.next;
		} while (cur != null); 
		return list.toArray(new RootPtr[0]);
	}

}
