package cn.timd.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.data.Stat;

import java.util.Arrays;
import java.util.List;

public class LeaderElection extends BaseConfiguration {
    private static final String namespace = "leader-election/test";
    private static final String zNodeNamePrefix = "node-";
    private volatile boolean isLeader = false;
    private final Object condition = new Object();
    private String nodeName;

    private final CuratorListener curatorListener = new CuratorListener() {
        public void eventReceived(CuratorFramework curatorFramework, CuratorEvent curatorEvent) throws Exception {
            if (curatorEvent
                    .getWatchedEvent()
                    .getType()
                    .compareTo(Watcher.Event.EventType.NodeDeleted) == 0)
                synchronized (condition) {
                    judgeIsLeader();
                }
        }
    };

    private final ConnectionStateListener connectionStateListener = new ConnectionStateListener() {
        public void stateChanged(CuratorFramework curatorFramework, ConnectionState connectionState) {
            System.out.println(connectionState);
            switch (connectionState) {
                case CONNECTED:
                case RECONNECTED:
                    synchronized (condition) {
                        if (nodeName != null)
                            try {
                                client.usingNamespace(namespace).delete().forPath("/" + nodeName);
                            } catch (Throwable ex) {
                                ex.printStackTrace();
                            }
                        register();
                        judgeIsLeader();
                    }
                    break;
                case SUSPENDED:
                case LOST:
                    synchronized (condition) {
                        isLeader = false;
                    }
                    break;
            }
        }
    };

    {
        client.start();
        client.getCuratorListenable().addListener(curatorListener);
        client.getConnectionStateListenable().addListener(connectionStateListener);
    }

    private void register() {
        try {
            nodeName = client.usingNamespace(namespace)
                    .create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(zNodeNamePrefix).substring(1);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private void judgeIsLeader() {
        try {
            List<String> brotherNames = client.getChildren().forPath("/" + namespace);
            String[] brotherArray = new String[brotherNames.size()];
            for (int i = 0; i < brotherNames.size(); ++i)
                brotherArray[i] = brotherNames.get(i);
            Arrays.sort(brotherArray);

            System.out.println("the least node is: " + brotherArray[0]);
            System.out.println("nodeName is: " + nodeName);
            if (nodeName.equals(brotherArray[0])) {
                System.out.println("begin leading");
                synchronized (condition) {
                    isLeader = true;
                    condition.notifyAll();
                }
                return;
            }

            String watchedNode = null;
            for (int i = 0; i <= brotherArray.length - 2; i++)
                if (brotherArray[i + 1].equals(nodeName))
                    watchedNode = brotherArray[i];
            System.out.println("watchedNode is: " + watchedNode);
            Stat stat = client.usingNamespace(namespace)
                .checkExists().watched().forPath(watchedNode);
            if (stat == null)
                judgeIsLeader();
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private void work() throws InterruptedException {
        while (true) {
            synchronized (condition) {
                if (!isLeader) {
                    condition.wait();
                    continue;
                }
                realLogic();
            }
        }
    }

    private void realLogic() throws InterruptedException {
        System.out.println("I am master ^_^");
        Thread.sleep(3000);
    }

    public static void main(String[] args) throws Throwable {
        new LeaderElection().work();
    }
}

