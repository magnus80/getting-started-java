package pages;

import com.codeborne.selenide.Selenide;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.cssClass;
import static com.codeborne.selenide.Selenide.$;

public class SearchPage {
    private final SelenideElement searchApp = $("react-app[app-name='blackbird-search']");
    private final SelenideElement searchInput = $("[aria-label='Search GitHub']");

    public SearchPage open() {
        Selenide.open("/search");
        searchApp.shouldHave(cssClass("loaded"));
        return this;
    }

    public SearchResultsPage searchFor(String query) {
        searchInput.setValue(query).pressEnter();
        return new SearchResultsPage();
    }
}
