package com.baidu.distributedlock.zookeeper;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import java.io.IOException;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class DistributedLock {

    private String lockId;

    private static final String LOCK_ROOT = "/LOCKS";

    //--------------------------------------------------------------
    // data为存储的节点数据内容
    // 由于锁机制用的是序列功能的特性，data的值不重要，只要利于网络传输即可
    //--------------------------------------------------------------
    private final static byte[] data = {0x12, 0x34};

    private final CountDownLatch latch = new CountDownLatch(1);

    private ZooKeeper zk;

    private int sessionTimeout;

    public DistributedLock(ZooKeeper zk, int sessionTimeout) {
        this.zk = zk;
        this.sessionTimeout = sessionTimeout;
    }

    public DistributedLock() throws IOException, KeeperException, InterruptedException {
        this.zk = ZookeeperClient.getInstance();
        this.sessionTimeout = ZookeeperClient.getSessionTimeout();
    }

    class LockWatcher implements Watcher {
        public void process(WatchedEvent event) {
            //--------------------------------------------------------------
            // 监控节点变化(本程序为序列的上一节点)
            // 若为节点删除，证明序列的上一节点已删除，此时释放阀门让当前的lock获得锁
            //--------------------------------------------------------------
            if (event.getType() == Event.EventType.NodeDeleted)
                latch.countDown();
        }
    }

    /**
     * @return
     * @throws KeeperException
     * @throws InterruptedException
     */
    public synchronized boolean lock() {

        //--------------------------------------------------------------
        // 保证锁根节点存在，若不存在则创建它
        //--------------------------------------------------------------
        createLockRootIfNotExists();

        try {

            lockId = zk.create(LOCK_ROOT + "/", data,
                    ZooDefs.Ids.OPEN_ACL_UNSAFE,
                    CreateMode.EPHEMERAL_SEQUENTIAL);

            System.out.println("thread " + Thread.currentThread().getName() +
                    " create the lock node: " + lockId + ", trying to get lock now");
            //--------------------------------------------------------------
            // 获得锁根节点下的各锁子节点，并排序
            //--------------------------------------------------------------
            List<String> nodes = zk.getChildren(LOCK_ROOT, true);
            SortedSet<String> sortedNode = new TreeSet<String>();
            for (String node : nodes) {
                sortedNode.add(LOCK_ROOT + "/" + node);
            }
            String first = sortedNode.first();
            SortedSet<String> lessThanMe = sortedNode.headSet(lockId);
            //--------------------------------------------------------------
            // 检查是否有比当前锁节点lockId更小的节点，若有则监控当前节点的前一节点
            //--------------------------------------------------------------
            if (lockId.equals(first)) {
                System.out.println("thread " + Thread.currentThread().getName() +
                        " has get the lock, lockId is " + lockId);
                return true;
            } else if (!lessThanMe.isEmpty()) {
                String prevLockId = lessThanMe.last();
                zk.exists(prevLockId, new LockWatcher());
                //--------------------------------------------------------------
                // 阀门等待sessionTimeout的时间
                // 当等待sessionTimeout的时间过后，上一个lock的Zookeeper连接会过期，删除所有临时节点，触发监听器
                //--------------------------------------------------------------
                latch.await(sessionTimeout, TimeUnit.MILLISECONDS);
                System.out.println("thread " + Thread.currentThread().getName() +
                        " has get the lock, lockId is " + lockId);
            }


        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return true;
    }


    public synchronized boolean unlock() {
        //--------------------------------------------------------------
        // 删除lockId节点以释放锁
        //--------------------------------------------------------------
        try {
            System.out.println("thread " + Thread.currentThread().getName() +
                    " unlock the lock: " + lockId + ", the node: " + lockId + " had been deleted");
            zk.delete(lockId, -1);
            return true;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        } finally {
            try {
                zk.close();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    /**
     * 保证锁根节点存在，若不存在则创建它
     */
    public void createLockRootIfNotExists() {
        try {
            Stat stat = zk.exists(LOCK_ROOT, false);
            if (stat == null) {
                zk.create(LOCK_ROOT, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        } catch (KeeperException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}
