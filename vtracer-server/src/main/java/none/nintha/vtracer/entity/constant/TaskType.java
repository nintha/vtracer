package none.nintha.vtracer.entity.constant;

import java.util.Arrays;
import java.util.Optional;

public enum TaskType {
    MEMBER_INFO(0, ""), MEMBER_ARCHIVE(1, ""), VIDEO_STAT(2, "");
    public int code;
    public String desc;

    TaskType(int c, String d) {
        code = c;
        desc = d;
    }

    public static Optional<TaskType> ofCode(int code) {
        return Arrays.stream(TaskType.values()).filter(e -> e.code == code).findAny();
    }

    public static Optional<TaskType> ofDesc(String desc) {
        return Arrays.stream(TaskType.values()).filter(e -> e.desc == desc).findAny();
    }
}
