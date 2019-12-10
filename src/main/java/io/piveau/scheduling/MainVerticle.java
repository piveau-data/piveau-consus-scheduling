package io.piveau.scheduling;

import io.piveau.pipe.PiveauCluster;
import io.piveau.scheduling.launcher.LauncherServiceVerticle;
import io.piveau.scheduling.quartz.QuartzService;
import io.piveau.scheduling.quartz.QuartzServiceVerticle;
import io.piveau.scheduling.shell.ShellVerticle;
import io.piveau.utils.ConfigHelper;
import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.RouterFactoryOptions;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;
import io.vertx.ext.web.handler.CorsHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainVerticle extends AbstractVerticle {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private QuartzService quartzService;

    @Override
    public void start(Promise<Void> startPromise) {
        ConfigStoreOptions envStoreOptions = new ConfigStoreOptions()
                .setType("env")
                .setConfig(new JsonObject().put("keys", new JsonArray()
                        .add("PIVEAU_CLUSTER_CONFIG")
                        .add("PIVEAU_SHELL_CONFIG")));

        ConfigRetriever retriever = ConfigRetriever.create(vertx, new ConfigRetrieverOptions().addStore(envStoreOptions));
        retriever.listen(configChange -> {

        });

        Promise<JsonObject> configPromise = Promise.promise();
        retriever.getConfig(configPromise);
        configPromise.future().compose(config -> {
            log.debug(config.encodePrettily());

            Promise<String> quartzPromise = Promise.promise();
            vertx.deployVerticle(QuartzServiceVerticle.class, new DeploymentOptions().setWorker(true).setConfig(config), quartzPromise);
            Promise<String> launcherPromise = Promise.promise();
            vertx.deployVerticle(LauncherServiceVerticle.class, new DeploymentOptions().setWorker(true).setConfig(config), launcherPromise);
            Promise<String> shellPromise = Promise.promise();
            vertx.deployVerticle(ShellVerticle.class, new DeploymentOptions().setWorker(true).setConfig(config), shellPromise);

            Promise<Void> verticlesPromise = Promise.promise();
            CompositeFuture.all(quartzPromise.future(), launcherPromise.future(), shellPromise.future()).setHandler(ar -> {
                if (ar.succeeded()) {
                    verticlesPromise.complete();
                } else {
                    verticlesPromise.fail(ar.cause());
                }
            });
            return verticlesPromise.future();
        }).compose(v -> {
            quartzService = QuartzService.createProxy(vertx, QuartzService.SERVICE_ADDRESS);

            Promise<OpenAPI3RouterFactory> factoryPromise = Promise.promise();
            OpenAPI3RouterFactory.create(vertx, "webroot/openapi.yaml", factoryPromise);
            return factoryPromise.future();
        }).setHandler(ar -> {
            if (ar.succeeded()) {
                OpenAPI3RouterFactory routerFactory = ar.result();
                RouterFactoryOptions options = new RouterFactoryOptions().setMountNotImplementedHandler(true);
                routerFactory.setOptions(options);
                routerFactory.addHandlerByOperationId("listTriggers", this::handleListTriggers);
                routerFactory.addHandlerByOperationId("getTriggers", this::handleGetTriggers);
                routerFactory.addHandlerByOperationId("createOrUpdateTriggers", this::handleCreateOrUpdateTriggers);
                routerFactory.addHandlerByOperationId("deleteTriggers", this::handleDeleteTriggers);
                routerFactory.addHandlerByOperationId("setTriggerStatus", this::handleSetTriggerStatus);
                routerFactory.addHandlerByOperationId("bulkUpdate", this::handleBulkUpdate);

                Router router = routerFactory.getRouter();
                router.route().order(0).handler(CorsHandler.create("*").allowedHeader("Content-Type").allowedMethods(Stream.of(HttpMethod.PUT, HttpMethod.GET).collect(Collectors.toSet())));

                router.route("/*").handler(StaticHandler.create());

                HealthCheckHandler hch = HealthCheckHandler.create(vertx);
                hch.register("buildInfo", future -> vertx.fileSystem().readFile("buildInfo.json", bi -> {
                    if (bi.succeeded()) {
                        future.complete(Status.OK(bi.result().toJsonObject()));
                    } else {
                        future.fail(bi.cause());
                    }
                }));
                router.get("/health").handler(hch);

                HttpServer server = vertx.createHttpServer(new HttpServerOptions().setPort(8080));
                server.requestHandler(router).listen();

                startPromise.complete();
            } else {
                startPromise.fail(ar.cause());
            }
        });
        retriever.listen(change -> {
            if (change.getNewConfiguration().containsKey("PIVEAU_CLUSTER_CONFIG")) {
                Object obj = change.getNewConfiguration().getValue("PIVEAU_CLUSTER_CONFIG");
                // hand over to vertx job factory...
            }
        });
    }

    private void handleListTriggers(RoutingContext routingContext) {
        quartzService.listTriggers(ar -> {
            if (ar.succeeded()) {
                routingContext.response().end(ar.result().encodePrettily());
            } else {
                routingContext.response().setStatusCode(500).end();
            }
        });
    }

    private void handleBulkUpdate(RoutingContext routingContext) {
        JsonObject bulk = routingContext.getBodyAsJson();

        ArrayList<Future> futureList = new ArrayList<>();

        bulk.fieldNames().forEach(name -> {
            JsonArray triggers = bulk.getJsonArray(name);
            Promise<String> promise = Promise.promise();
            quartzService.createOrUpdateTrigger(name, triggers, promise);
            futureList.add(promise.future());
        });
        CompositeFuture.join(futureList).setHandler(ar -> {
            if (ar.succeeded()) {
                routingContext.response().end();
            } else {
                routingContext.response().setStatusCode(200).setStatusMessage("Not all triggers were successfully created or updated.").end();
            }
        });
    }

    private void handleGetTriggers(RoutingContext routingContext) {
        String pipeId = routingContext.pathParam("pipeId");
        quartzService.getTriggers(pipeId, ar -> {
            if (ar.succeeded()) {
                routingContext.response().end(ar.result().encodePrettily());
            } else {
                routingContext.response().setStatusCode(404).putHeader("Content-Type", "text/plain").end(ar.cause().getMessage());
            }
        });
    }

    private void handleCreateOrUpdateTriggers(RoutingContext routingContext) {
        String pipeId = routingContext.pathParam("pipeId");
        JsonArray triggers = routingContext.getBodyAsJsonArray();
        quartzService.createOrUpdateTrigger(pipeId, triggers, ar -> {
            if (ar.succeeded()) {
                int code = "created".equalsIgnoreCase(ar.result()) ? 201 : 200;
                routingContext.response().setStatusCode(code).end();
            } else {
                routingContext.response().setStatusCode(500).end();
            }
        });
    }

    private void handleDeleteTriggers(RoutingContext routingContext) {
        String pipeId = routingContext.pathParam("pipeId");
        quartzService.deleteTriggers(pipeId, ar -> {
            if (ar.succeeded()) {
                routingContext.response().end();
            } else {
                routingContext.response().setStatusCode(404).end();
            }
        });
    }

    private void handleSetTriggerStatus(RoutingContext routingContext) {
        String pipeId = routingContext.pathParam("pipeId");
        String triggerId = routingContext.pathParam("triggerId");
        String status = routingContext.pathParam("status");

        quartzService.setTriggerStatus(pipeId, triggerId, status, ar -> {
            if (ar.succeeded()) {
                routingContext.response().end(ar.result());
            } else {
                String cause = ar.cause().getMessage();
                if (cause.contains("not found")) {
                    routingContext.response().setStatusCode(404).end(cause);
                } else if (cause.equals("status already set or unknown")) {
                    routingContext.response().setStatusCode(409).end(cause);
                } else {
                    routingContext.response().setStatusCode(500).end(cause);
                }
            }
        });

    }

    public static void main(String[] args) {
        String[] params = Arrays.copyOf(args, args.length + 1);
        params[params.length - 1] = MainVerticle.class.getName();
        Launcher.executeCommand("run", params);
    }

}
