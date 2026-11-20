package local.money;
import org.json.*;
import java.nio.file.*;
import java.lang.reflect.*;
public class SberCapitalTest {
    static void check(boolean value){if(!value)throw new AssertionError();}
    public static void main(String[] args)throws Exception {
        Method method=DirectSber.class.getDeclaredMethod("capitalSnapshot",JSONObject.class);method.setAccessible(true);
        JSONObject model=new JSONObject("{\"CTACCOUNTS\":{\"state\":\"SUCCESS\",\"errors\":[],\"data\":[{\"id\":\"credit\",\"state\":\"OPENED\",\"number\":\"123456\",\"balance\":{\"currency\":{\"code\":\"RUB\"},\"amount\":\"9000\"},\"availableTotalLimit\":{\"amount\":\"9000\"},\"creditLimit\":{\"amount\":\"10000\"},\"cards\":[{\"id\":\"card-one\"},{\"id\":\"card-two\"}]}]}}");
        JSONObject result=(JSONObject)method.invoke(new DirectSber(),model);
        check(result.getJSONArray("items").length()==1);
        JSONObject account=result.getJSONArray("items").getJSONObject(0);
        check(account.getString("amount").equals("-1000"));check(!account.toString().contains("123456"));
        JSONObject ordinary=new JSONObject("{\"ACCOUNTS\":{\"state\":\"SUCCESS\",\"errors\":[],\"data\":[{\"id\":\"current\",\"state\":\"OPENED\",\"accountType\":\"CurrentAccount\",\"number\":\"987654321\",\"balance\":{\"currency\":{\"code\":\"RUB\"},\"amount\":\"1234.50\"}}]}}");
        DirectSber client=new DirectSber();JSONObject current=(JSONObject)method.invoke(client,ordinary);
        check(current.getJSONArray("items").length()==1);check(current.getJSONArray("unsupported").length()==0);
        JSONObject entry=current.getJSONArray("items").getJSONObject(0);check(entry.getString("amount").equals("1234.50"));check(entry.getString("suffix").equals("4321"));check(!entry.toString().contains("987654321"));
        Method resource=DirectSber.class.getDeclaredMethod("resourceKey",JSONObject.class);resource.setAccessible(true);
        check(resource.invoke(client,new JSONObject("{\"id\":\"account:current\"}")).equals(entry.getString("key")));
        check(resource.invoke(client,new JSONObject("{\"id\":\"unknown\"}"))==null);
        DirectSber cardsClient=new DirectSber();JSONObject cardResult=(JSONObject)method.invoke(cardsClient,model);
        check(resource.invoke(cardsClient,new JSONObject("{\"id\":\"card:card-two\"}")).equals(cardResult.getJSONArray("items").getJSONObject(0).getString("key")));
        Method merge=DirectSber.class.getDeclaredMethod("mergeCatalogue",JSONObject.class,JSONObject.class);merge.setAccessible(true);
        JSONObject full=(JSONObject)merge.invoke(client,new JSONObject(),new JSONObject("{\"accounts\":{\"data\":[{\"id\":\"payment\",\"state\":\"OPENED\",\"number\":\"12345678901234564321\",\"name\":\"Платёжный\",\"balance\":{\"amount\":\"500\",\"currency\":{\"code\":\"RUB\"}}}]},\"brokerAccounts\":{\"data\":[{\"id\":\"broker\",\"name\":\"Брокерский счёт\"}]}}"));
        JSONObject complete=(JSONObject)method.invoke(client,full);check(complete.getJSONArray("items").length()==2);
        check(complete.getJSONArray("items").getJSONObject(0).getString("suffix").equals("4321"));
        check(complete.getJSONArray("items").getJSONObject(1).isNull("amount"));
        check(!complete.getJSONArray("diagnostics").toString().contains("4321"));
        full.getJSONObject("brokerAccounts").getJSONArray("data").getJSONObject(0).put("balance",new JSONObject("{\"amount\":\"100\",\"currency\":{\"code\":\"RUB\"}}"));
        JSONObject brokerCash=(JSONObject)method.invoke(client,full);check(brokerCash.getJSONArray("items").getJSONObject(1).isNull("amount"));
        model.getJSONObject("CTACCOUNTS").put("state","ERROR");boolean failed=false;
        try{method.invoke(new DirectSber(),model);}catch(InvocationTargetException expected){failed=expected.getCause() instanceof java.io.IOException;}check(failed);
        JSONObject fixture=new JSONObject(Files.readString(Path.of("research/modern-fixtures_accounts.json")));
        JSONObject captured=(JSONObject)method.invoke(new DirectSber(),fixture);captured.put("timestamp",1);
        Files.createDirectories(Path.of(".test-classes"));Files.writeString(Path.of(".test-classes/capital-reference.json"),captured.toString());
        check(captured.getJSONArray("items").length()>0);
        System.out.println("Sber capital extraction checks passed: credit liability, one account for multiple cards, protected identifiers, incomplete groups rejected, reference DTO.");
    }
}
