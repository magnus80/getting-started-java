package pages;

import com.codeborne.selenide.SelenideElement;
import io.qameta.allure.Step;

import static com.codeborne.selenide.Condition.exactText;
import static com.codeborne.selenide.Selenide.$;
import static com.codeborne.selenide.Selenide.webdriver;
import static com.codeborne.selenide.WebDriverConditions.urlContaining;

public class RepositoryPage {
    private final SelenideElement repoName = $("[data-testid='repo-name-wrapper']");

    @Step("Check that repository {fullName} is opened")
    public RepositoryPage shouldBeOpened(String fullName) {
        webdriver().shouldHave(urlContaining("/" + fullName));
        repoName.shouldHave(exactText(fullName.substring(fullName.indexOf('/') + 1)));
        return this;
    }
}
