package norym.logisticsdeliverysystem;

import norym.logisticsdeliverysystem.config.PricingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(PricingProperties.class)
public class LogisticsDeliverySystemApplication
{

    public static void main(String[] args)
    {
        SpringApplication.run(LogisticsDeliverySystemApplication.class, args);
    }

}
