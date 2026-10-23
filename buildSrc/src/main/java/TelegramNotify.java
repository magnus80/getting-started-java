import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.time.Duration;

public abstract class TelegramNotify extends DefaultTask {

    @Internal
    public abstract DirectoryProperty getResultsDir();

    @Input
    public abstract Property<String> getJobStatus();

    @Input
    public abstract Property<String> getCompileOutcome();

    @Input
    public abstract Property<String> getTestsOutcome();

    @Input
    public abstract Property<String> getProjectName();

    @Input
    public abstract Property<String> getCiName();

    @Input
    public abstract Property<String> getBranch();

    @Input
    public abstract Property<String> getCommit();

    @Input
    public abstract Property<String> getRunUrl();

    @Inject
    protected abstract ProviderFactory getProviders();

    @TaskAction
    public void send() {
        TelegramNotifier.Run run = new TelegramNotifier.Run(
                getJobStatus().get(), getCompileOutcome().get(), getTestsOutcome().get(),
                getResultsDir().get().getAsFile().toPath(),
                getProjectName().get(), getCiName().get(), getBranch().get(), getCommit().get(), getRunUrl().get());
        try {
            TelegramNotifier.notify(run,
                    getProviders().environmentVariable("TELEGRAM_API_URL").getOrElse("https://api.telegram.org"),
                    getProviders().environmentVariable("TELEGRAM_BOT_TOKEN").getOrElse(""),
                    getProviders().environmentVariable("TELEGRAM_CHAT_ID").getOrElse(""),
                    Duration.ofSeconds(10),
                    getLogger()::lifecycle);
        } catch (TelegramNotifier.NotifyException e) {
            getLogger().error("::error::" + e.getMessage());
            throw new GradleException(e.getMessage());
        }
    }
}
