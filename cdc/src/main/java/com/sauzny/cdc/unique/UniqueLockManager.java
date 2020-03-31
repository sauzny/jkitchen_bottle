package com.sauzny.cdc.unique;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Lists;
import com.google.common.collect.Table;

import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicLong;

public class UniqueLockManager {

    // tableName -> (tableName && uniqueColumn && Value -> lock)
    //private Map<String, Map<String, UniqueLock>> uniqueLockTable = new HashMap<>();
    private static Table<String, String, UniqueLock> uniqueLockTable = HashBasedTable.create();

    private static AtomicLong atomicLong = new AtomicLong(0L);

    public static void hold(User user) {
        // 全局事件id
        long eventId = atomicLong.incrementAndGet();

        // 模拟 获取 key1
        String key1 = "user";
        if (user instanceof User2) {
            key1 = "user2";
        }

        for (String column : Lists.newArrayList("name", "type")) {

            // 模拟 获取 key2
            String value = user.getName();
            if ("type".equals(column)) {
                value = user.getType();
            }
            String key2 = new StringJoiner("_").add(key1).add(column).add(value).toString();

            // 获取 lock 对象
            UniqueLock uniqueLock = uniqueLockTable.get(key1, key2);
            if (uniqueLock == null) {
                uniqueLock = new UniqueLock();
                uniqueLockTable.put(key1, key2, uniqueLock);
            }

            // 制作 lock
            uniqueLock.setWait_count(uniqueLock.getWait_count() + 1);
            long wait_event_id = uniqueLock.getEvent_id();
            uniqueLock.setEvent_id(eventId);
            // true 持有 false 释放
            uniqueLock.getCond_map().put(eventId, true);

            //
            user.setEventId(eventId);
            user.getWaitEventIdSet().add(wait_event_id);
        }
    }

    public static void releaseAndApply(User user, UniqueLockDealBusiness  uniqueLockDealBusiness) {

        List<UniqueLock> uniqueLockList = Lists.newArrayList();

        // 模拟 获取 key1
        String key1 = "user";
        if (user instanceof User2) {
            key1 = "user2";
        }

        try {
            for (String column : Lists.newArrayList("name", "type")) {

                // 模拟 获取 key2
                String value = user.getName();
                if ("type".equals(column)) {
                    value = user.getType();
                }
                String key2 = new StringJoiner("_").add(key1).add(column).add(value).toString();

                UniqueLock uniqueLock = uniqueLockTable.get(key1, key2);

                synchronized (uniqueLock) {
                    boolean isAllRelease = false;
                    while (!isAllRelease) {
                        for(long waitEventId : user.getWaitEventIdSet()){
                            if (waitEventId != 0 && uniqueLock.getCond_map().getOrDefault(waitEventId, false)) {
                                System.out.println(user + "  " + key1 + "  " + key2 + "  我要排队等待  " + waitEventId);
                                uniqueLock.wait();
                                isAllRelease = false;
                                break;
                            } else {
                                isAllRelease = true;
                            }
                        }
                    }
                }

                uniqueLockList.add(uniqueLock);

            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(user + "获取锁时，发生异常" + e.getMessage());
        }

        // do the business
        uniqueLockDealBusiness.apply();


        // 释放锁
        uniqueLockList.forEach(uniqueLock -> {
            synchronized (uniqueLock) {
                uniqueLock.setWait_count(uniqueLock.getWait_count() - 1);
                uniqueLock.getCond_map().put(user.getEventId(), false);
                uniqueLock.notifyAll();
            }
        });
    }

    @FunctionalInterface
    interface UniqueLockDealBusiness {
        void apply();
    }
}
