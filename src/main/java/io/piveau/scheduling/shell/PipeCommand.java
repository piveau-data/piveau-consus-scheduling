package io.piveau.scheduling.shell;

import io.piveau.pipe.*;
import io.piveau.scheduling.launcher.LauncherService;
import io.vertx.core.Vertx;
import io.vertx.core.cli.Argument;
import io.vertx.core.cli.CLI;
import io.vertx.core.cli.CommandLine;
import io.vertx.core.cli.Option;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.shell.command.Command;
import io.vertx.ext.shell.command.CommandBuilder;

public class PipeCommand {
    private Command command;

    private LauncherService launcherService;

    private PipeCommand(Vertx vertx) {
        launcherService = LauncherService.createProxy(vertx, LauncherService.SERVICE_ADDRESS);
        command = CommandBuilder.command(
                CLI.create("pipe")
                        .addArgument(
                                new Argument().setIndex(0)
                                        .setArgName("pipeName")
                                        .setRequired(false)
                                        .setDescription("Name of the pipe."))
                        .addArgument(
                                new Argument().setIndex(1)
                                        .setArgName("pipeAction")
                                        .setRequired(false)
                                        .setDescription("Action on the pipe"))
                        .addOption(new Option().setHelp(true).setFlag(true).setArgName("help").setShortName("h").setLongName("help"))
                        .addOption(new Option().setFlag(true).setArgName("verbose").setShortName("v").setLongName("verbose"))
        ).processHandler(process -> {
            CommandLine commandLine = process.commandLine();
            String pipeName = commandLine.getArgumentValue(0);
            if (pipeName == null) {
                launcherService.availablePipes(ar -> {
                    if (ar.succeeded()) {
                        ar.result().forEach(pipe -> process.write(pipe.getJsonObject("header").getString("name") + "\n"));
                    } else {
                        process.write("Pipe list not available.\n");
                    }
                    process.end();
                });
            } else {
                String action = commandLine.getArgumentValue(1);
                switch (action) {
                    case "launch":
                        launcherService.launch(pipeName, new JsonObject(), ar -> {
                            if (ar.succeeded()) {
                                process.write("Pipe " + pipeName + " successfully launched.\n");
                            } else {
                                process.write("Pipe launch failed: " + ar.cause().getMessage());
                            }
                            process.end();
                        });
                        break;
                    case "show":
                        launcherService.getPipe(pipeName, ar -> {
                            if (ar.succeeded()) {
                                process.write("\n" + ar.result().encodePrettily() + "\n");
                            } else {
                                process.write(ar.cause().getMessage());
                            }
                            process.end();
                        });
                        break;
                    default:
                        process.write("Unknown pipe action.\n").end();
                }
            }
        }).build(vertx);
    }

    public static Command create(Vertx vertx) {
        return new PipeCommand(vertx).command;
    }

}
