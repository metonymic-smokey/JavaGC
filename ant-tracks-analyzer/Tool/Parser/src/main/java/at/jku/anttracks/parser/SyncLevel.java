
package at.jku.anttracks.parser;

public enum SyncLevel {
    NONE(0),
    ENSURE_ORDER(1),
    FULL(2);

    private final int id;

    private SyncLevel(int id) {
        this.id = id;
    }

    public static SyncLevel parse(int id) {
        for (SyncLevel level : values()) {
            if (id == level.id) {
                return level;
            }
        }
        return null;
    }
}
