package none.nintha.vtracer.util;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CollectionUtil
 */
public class CollUtil {
    /**
     * 对list进行均匀缩小，按index取模后过滤
     */
    public static <T> List<T> shrink(List<T> list, int targetSize){
        if (list.size() >= targetSize) {
            int interval = list.size() / targetSize + 1;
            int[] index = {0};
            return list.stream().filter(vs -> index[0]++ % interval == 0).collect(Collectors.toList());
        }
        return list;
    }
}
