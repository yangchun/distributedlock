package com.baidu.distributedlock.zookeeper;

import org.apache.zookeeper.KeeperException;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;

public class Test {
    public static void main(String[] args) throws IOException, KeeperException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(10);
        for (int i = 0; i < 10; i++) {
            new Thread(new Runnable() {
                public void run() {
                    DistributedLock lock = null;
                    try {
                        lock = new DistributedLock();
                        latch.countDown();
                        latch.await();
                        lock.lock();
                        Thread.sleep(200);
                    }  catch (InterruptedException e) {
                        e.printStackTrace();
                    } catch (KeeperException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (lock != null) {
                            lock.unlock();
                        }
                    }
                }
            }).start();
        }
    }
}
