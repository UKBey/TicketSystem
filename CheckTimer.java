import org.kie.server.api.model.admin.TimerInstance;
import java.lang.reflect.Method;

public class CheckTimer {
    public static void main(String[] args) {
        for(Method m : TimerInstance.class.getMethods()) {
            if(m.getName().startsWith("get")) {
                System.out.println(m.getName() + " -> " + m.getReturnType().getName());
            }
        }
    }
}
