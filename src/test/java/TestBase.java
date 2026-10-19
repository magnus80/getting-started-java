import com.codeborne.selenide.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static com.codeborne.selenide.Selenide.closeWebDriver;

class TestBase {
    @BeforeEach
    void setUp() {
        Configuration.baseUrl = System.getProperty("baseUrl");
        Configuration.browser = System.getProperty("browser");
        Configuration.browserSize = System.getProperty("browserSize");
        Configuration.headless = Boolean.parseBoolean(System.getProperty("headless"));
    }

    @AfterEach
    void tearDown() {
        closeWebDriver();
    }
}
