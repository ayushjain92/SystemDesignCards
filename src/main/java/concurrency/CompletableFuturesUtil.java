package concurrency;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class CompletableFuturesUtil {

    private Integer random() {
        System.out.println("random(): " + Thread.currentThread().getName());
        Integer i = new Random().nextInt(100);
        System.out.println("i: " + Thread.currentThread().getName());
        return i;
    }

    private String convertToString(Integer a) {
        System.out.println("convertToString: " + Thread.currentThread().getName());
        return a.toString();
    }

    public void testCF() throws Exception {
        System.out.println("testCF: " + Thread.currentThread().getName());
        CompletableFuture<Void> cf = CompletableFuture.
                runAsync(() -> random()).thenRun(() -> System.out.println("yodo"));
                        //.thenApplyAsync(a -> convertToString(a));
        //System.out.println(cf.get());
    }


    public static void main(String[] args) throws Exception {

        CompletableFuturesUtil cfu = new CompletableFuturesUtil();
        cfu.testCF();
    }
}