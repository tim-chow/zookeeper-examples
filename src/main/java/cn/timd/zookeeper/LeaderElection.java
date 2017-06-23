package cn.timd.zookeeper;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.CuratorEvent;
import org.apache.curator.framework.api.CuratorListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.Watcher;

import java.util.Arrays;
import java.util.List;

public class LeaderElection extends BaseConfiguration {
    private static final String namespace = "leader-election/test";
    private static final String zNodeNamePrefix = "node-";
    private volatile boolean isLeader = false;
    private final Object condition = new Object();
    private String nodeName;

    private final CuratorListener listener = new CuratorListener() {
        public void eventReceived(CuratorFramework curatorFramework, CuratorEvent curatorEvent) throws Exception {
            if (curatorEvent
                    .getWatchedEvent()
                    .getType()
                    .compareTo(Watcher.Event.EventType.NodeDeleted) == 0)
                judgeIsLeader();
        }
    };

    {
        try {
            client.start();
            nodeName = client.usingNamespace(namespace)
                    .create()
                    .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                    .forPath(zNodeNamePrefix).substring(1);
            client.getCuratorListenable().addListener(listener);
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
                isLeader = true;
                synchronized (condition) {
                    condition.notifyAll();
                }
                return;
            }

            String watchedNode = null;
            for (int i = 0; i <= brotherArray.length - 2; i++)
                if (brotherArray[i + 1].equals(nodeName))
                    watchedNode = brotherArray[i];
            System.out.println("watchedNode is: " + watchedNode);
            client.usingNamespace(namespace)
                .getData().watched().forPath(watchedNode);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        }
    }

    private void work() throws InterruptedException {
        judgeIsLeader();
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

    private void realLogic() {
        System.out.println("I am master ^_^");
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        new LeaderElection().work();
    }
}