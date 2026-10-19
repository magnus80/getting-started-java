package pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

public class SearchResultsPage {
    private final SelenideElement resultsList = $("[data-testid='results-list']");
    private final ElementsCollection headings = $$("h3");

    public SearchResultsPage shouldHaveResult(String expectedText) {
        resultsList.shouldHave(text(expectedText));
        return this;
    }

    public SearchResultsPage shouldBeEmpty() {
        headings.findBy(text("Your search did not match any repositories")).shouldBe(visible);
        resultsList.shouldNot(exist);
        return this;
    }

    public RepositoryPage openRepository(String fullName) {
        resultsList.$("a[href='/" + fullName + "']").click();
        return new RepositoryPage();
    }
}
