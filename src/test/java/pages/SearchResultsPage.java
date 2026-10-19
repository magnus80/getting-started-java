package pages;

import com.codeborne.selenide.ElementsCollection;
import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

import static com.codeborne.selenide.Condition.exist;
import static com.codeborne.selenide.Condition.text;
import static com.codeborne.selenide.Condition.visible;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.$$;

public class SearchResultsPage {
    private final SelenideElement resultsList = $("[data-testid='results-list']");
    private final ElementsCollection headings = $$("h3");

    @Step("Check that search results contain \"{expectedText}\"")
    public SearchResultsPage shouldHaveResult(String expectedText) {
        resultsList.shouldHave(text(expectedText));
        return this;
    }

    @Step("Check that search results are empty")
    public SearchResultsPage shouldBeEmpty() {
        headings.findBy(text("Your search did not match any repositories")).shouldBe(visible);
        resultsList.shouldNot(exist);
        return this;
    }

    @Step("Open repository {fullName} from search results")
    public RepositoryPage openRepository(String fullName) {
        resultsList.$("a[href='/" + fullName + "']").click();
        return new RepositoryPage();
    }
}
