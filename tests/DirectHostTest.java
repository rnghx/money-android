package local.money;
import java.net.URL;
public class DirectHostTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        check(DirectSber.allowed(new URL("https://online.sberbank.ru:4477/CSAMAPI/login.do")));
        check(DirectSber.allowed(new URL("https://mobile-node3.online.sberbank.ru:8543/uoh-bh/v1/operations/list")));
        check(!DirectSber.allowed(new URL("http://online.sberbank.ru:4477/")));
        check(!DirectSber.allowed(new URL("https://online.sberbank.ru.evil.example:4477/")));
        check(!DirectSber.allowed(new URL("https://evil.example:8543/")));
        check(!DirectSber.allowed(new URL("https://name@online.sberbank.ru:4477/")));
        check(!DirectSber.allowed(new URL("https://online.sberbank.ru:443/")));
        System.out.println("Bank endpoint allowlist checks passed.");
    }
}
