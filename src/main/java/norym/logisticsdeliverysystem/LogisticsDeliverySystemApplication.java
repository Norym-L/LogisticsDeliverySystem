package norym.logisticsdeliverysystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogisticsDeliverySystemApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(LogisticsDeliverySystemApplication.class, args);
    }

}
