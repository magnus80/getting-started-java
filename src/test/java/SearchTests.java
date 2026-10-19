import org.junit.jupiter.api.Test;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;
import static com.codeborne.selenide.Selenide.open;
import static com.codeborne.selenide.Selenide.webdriver;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;

class SearchTests extends TestBase {
    @Test
    void successfulSearchTest() {
        open("/search");
        $("react-app[app-name='blackbird-search']").shouldHave(cssClass("loaded"));
        $("[aria-label='Search GitHub']").setValue("qa.guru").pressEnter();
        $("[data-testid='results-list']").shouldHave(text("QA.GURU"));
    }

    @Test
    void openRepositoryFromSearchResultsTest() {
        open("/search");
        $("react-app[app-name='blackbird-search']").shouldHave(cssClass("loaded"));
        $("[aria-label='Search GitHub']").setValue("selenide").pressEnter();
        $("[data-testid='results-list'] a[href='/selenide/selenide']").click();
        webdriver().shouldHave(urlContaining("/selenide/selenide"));
        $("[data-testid='repo-name-wrapper']").shouldHave(exactText("selenide"));
    }

    @Test
    void noResultsForImpossibleStarsRangeTest() {
        open("/search");
        $("react-app[app-name='blackbird-search']").shouldHave(cssClass("loaded"));
        $("[aria-label='Search GitHub']").setValue("stars:<0").pressEnter();
        $$("h3").findBy(text("Your search did not match any repositories")).shouldBe(visible);
        $("[data-testid='results-list']").shouldNot(exist);
    }
}
