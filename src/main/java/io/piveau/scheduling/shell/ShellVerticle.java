package io.piveau.scheduling.shell;

import io.piveau.scheduling.launcher.LauncherService;
import io.piveau.utils.ConfigHelper;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.shell.ShellService;
import io.vertx.ext.shell.ShellServiceOptions;
import io.vertx.ext.shell.command.CommandBuilder;
import io.vertx.ext.shell.command.CommandProcess;
import io.vertx.ext.shell.command.CommandRegistry;
import io.vertx.ext.shell.term.HttpTermOptions;
import io.vertx.ext.shell.term.TelnetTermOptions;

public class ShellVerticle extends AbstractVerticle {

    @Override
    public void start(Promise<Void> promise) {

        ShellServiceOptions shellServiceOptions = new ShellServiceOptions().setWelcomeMessage("\n piveau scheduling shell\n\n");

        JsonObject clientConfig = ConfigHelper.forConfig(config()).forceJsonObject("PIVEAU_SHELL_CONFIG");
        clientConfig.forEach(entry -> {
            JsonObject options = (JsonObject) entry.getValue();
            switch (entry.getKey()) {
                case "telnet":
                    shellServiceOptions.setTelnetOptions(new TelnetTermOptions()
                            .setHost(options.getString("host", "0.0.0.0"))
                            .setPort(options.getInteger("port", 5000)));
                    break;
                case "http":
                    shellServiceOptions.setHttpOptions(new HttpTermOptions()
                            .setHost(options.getString("host", "0.0.0.0"))
                            .setPort(options.getInteger("port", 8085)));
                    break;
            }
        });

        ShellService service = ShellService.create(vertx, shellServiceOptions);
        service.start(ar -> {
            if (ar.succeeded()) {
                CommandRegistry.getShared(vertx)
                        .registerCommand(TriggerCommand.create(vertx))
                        .registerCommand(PipeCommand.create(vertx));
                promise.complete();
            } else {
                promise.fail(ar.cause());
            }
        });
    }

}
