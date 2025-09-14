import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;

public class TempTest {
    public static void main(String[] args) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .build();
        
        // Попробуем разные варианты названий методов
        System.out.println("Available methods:");
        System.out.println("waitDurationInOpenState: " + config.getWaitDurationInOpenState());
    }
}
