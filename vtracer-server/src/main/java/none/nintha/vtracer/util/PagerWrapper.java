package none.nintha.vtracer.util;

import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

public class PagerWrapper {
    private static final Logger logger = LoggerFactory.getLogger(PagerWrapper.class);
    private final static ExecutorService executors = Executors.newFixedThreadPool(8);
    private final static int PAGE_SIZE = 500;

    public static <R> List<R> list(long total, BiFunction<Long, Integer, List<R>> func) {
        return shrink(total, 1, func);
    }

    /**
     * 间隔取值，每interval个元素取一个值
     */
    public static <R> List<R> shrink(long total, int interval, BiFunction<Long, Integer, List<R>> func) {
        if (total == 0) return Collections.emptyList();

        List<List<R>> wrapList = Lists.newArrayList();
        long pageTotal = total / PAGE_SIZE + (total % PAGE_SIZE == 0 ? 0 : 1);
//        logger.info("一共 " + pageTotal);
        final CountDownLatch latch = new CountDownLatch((int) pageTotal);
        for (long skip = 0; skip < total; skip += PAGE_SIZE) {
            final long skipTemp = skip;
            final List<R> sublist = Lists.newArrayList();
            wrapList.add(sublist);
            executors.execute(() -> {
                List<R> tlist = func.apply(skipTemp, PAGE_SIZE);
                int[] index = {0};
                tlist = tlist.stream().filter(v -> index[0]++ % interval == 0).collect(Collectors.toList());
                sublist.addAll(tlist);
                latch.countDown();
//                logger.info("剩下 " + latch.getCount());
            });
        }
        try {
            latch.await();
        } catch (InterruptedException e) {
            logger.error("PagerWrapper 异常", e);
            return Collections.emptyList();
        }
        return wrapList.stream().flatMap(List::stream).collect(Collectors.toList());
    }

}
