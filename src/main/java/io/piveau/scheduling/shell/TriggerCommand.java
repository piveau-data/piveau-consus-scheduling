package io.piveau.scheduling.shell;

import io.piveau.scheduling.quartz.QuartzService;
import io.vertx.core.Vertx;
import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.shell.command.Command;
import io.vertx.ext.shell.command.CommandBuilder;

public class TriggerCommand {

    private Command command;

    private QuartzService quartzService;

    private TriggerCommand(Vertx vertx) {
        quartzService = QuartzService.createProxy(vertx, QuartzService.SERVICE_ADDRESS);
        command = CommandBuilder.command(
                CLI.create("trigger")
                        .addArgument(
                                new Argument()
                                        .setArgName("pipeName")
                                        .setRequired(true)
                                        .setDescription("The name of the pipe."))
                        .addOption(new Option().setHelp(true).setFlag(true).setArgName("help").setShortName("h").setLongName("help"))
                        .addOption(new Option().setFlag(true).setArgName("verbose").setShortName("v").setLongName("verbose"))
        ).processHandler(process -> {
            CommandLine commandLine = process.commandLine();
            String pipeName = commandLine.getArgumentValue(0);
            quartzService.getTriggers(pipeName, ar -> {
                if (ar.succeeded()) {
                    JsonArray triggers = ar.result();
                    process.write("\n" + triggers.encodePrettily() + "\n");
                } else {
                    process.write(ar.cause().getMessage() + "\n");
                }
                process.end();
            });
        }).build(vertx);
    }

    public static Command create(Vertx vertx) {
        return new TriggerCommand(vertx).command;
    }

}
