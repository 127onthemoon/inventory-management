import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** 通过 Chrome DevTools 协议截图：java Shot.java <wsUrl> <JSESSIONID> <outDir> name=url ... */
public class Shot {
    static final Map<Integer, CompletableFuture<String>> pending = new ConcurrentHashMap<>();
    static int id = 0;
    static WebSocket ws;

    static String call(String method, String params) throws Exception {
        int myId = ++id;
        CompletableFuture<String> f = new CompletableFuture<>();
        pending.put(myId, f);
        ws.sendText("{\"id\":" + myId + ",\"method\":\"" + method + "\",\"params\":" + params + "}", true).get();
        return f.get(30, TimeUnit.SECONDS);
    }

    public static void main(String[] a) throws Exception {
        StringBuilder buf = new StringBuilder();
        ws = HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(a[0]), new WebSocket.Listener() {
            public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
                buf.append(data);
                if (last) {
                    String msg = buf.toString(); buf.setLength(0);
                    Matcher m = Pattern.compile("^\\{\"id\":(\\d+)").matcher(msg);
                    if (m.find()) { var f = pending.remove(Integer.parseInt(m.group(1))); if (f != null) f.complete(msg); }
                }
                w.request(1);
                return null;
            }
        }).get();
        call("Network.enable", "{}");
        call("Network.setCookie", "{\"name\":\"JSESSIONID\",\"value\":\"" + a[1] + "\",\"domain\":\"127.0.0.1\",\"path\":\"/\"}");
        call("Emulation.setDeviceMetricsOverride", "{\"width\":1440,\"height\":900,\"deviceScaleFactor\":1,\"mobile\":false}");
        for (int i = 3; i < a.length; i++) {
            String[] kv = a[i].split("=", 2);
            call("Page.navigate", "{\"url\":\"" + kv[1] + "\"}");
            Thread.sleep(1800);
            String r = call("Page.captureScreenshot", "{\"format\":\"png\"}");
            Matcher m = Pattern.compile("\"data\":\"([^\"]+)\"").matcher(r);
            if (m.find()) {
                Files.write(Path.of(a[2], kv[0] + ".png"), Base64.getDecoder().decode(m.group(1)));
                System.out.println("saved " + kv[0]);
            } else System.out.println("fail " + kv[0] + " " + r.substring(0, Math.min(200, r.length())));
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "").get();
    }
}
