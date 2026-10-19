package tests;

import org.junit.jupiter.api.Test;
import pages.SearchPage;

class SearchTests extends TestBase {
    private final SearchPage searchPage = new SearchPage();

    @Test
    void successfulSearchTest() {
        searchPage.open()
                .searchFor("qa.guru")
                .shouldHaveResult("QA.GURU");
    }

    @Test
    void openRepositoryFromSearchResultsTest() {
        searchPage.open()
                .searchFor("selenide")
                .openRepository("selenide/selenide")
                .shouldBeOpened("selenide/selenide");
    }

    @Test
    void noResultsForImpossibleStarsRangeTest() {
        searchPage.open()
                .searchFor("stars:<0")
                .shouldBeEmpty();
    }
}
