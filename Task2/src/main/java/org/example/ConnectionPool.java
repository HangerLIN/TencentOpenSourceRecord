package org.example;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectionPool {
    private static Lock lock = new ReentrantLock();

    static ConnectionNode head = null;
    static final int connectionCount = 16;

    public static ConnectionNode getConnection() {
        lock.lock();
        if (head != null) {
            ConnectionNode target = head;
            head = head.next;
            lock.unlock();
            return target;
        }

        lock.unlock();

        try {
            Thread.sleep(100);
        } catch (Exception e) {
        }

        return null;
    }

    public static void releaseConnection(ConnectionNode node) {
        addConnectionNode(node);
    }

    public static void addConnectionNode(ConnectionNode current) {
        lock.lock();

        current.next = head;
        head = current;
        lock.unlock();
    }

    public static void initConnectionPool() {
        try {
            for (int i = 0; i < connectionCount; i++) {
                ConnectionNode current = new ConnectionNode();
                addConnectionNode(current);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void closeConnection() {
        try {
            for (int i = 0; i < connectionCount; i++) {
                ConnectionNode node;
                do {
                    node = getConnection();
                } while (node == null);

                if (node.stm != null) {
                    node.stm.close();
                }

                if (node.con != null) {
                    node.con.close();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
