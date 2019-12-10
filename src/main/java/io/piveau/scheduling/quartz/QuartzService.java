package io.piveau.scheduling.quartz;

import io.piveau.scheduling.launcher.LauncherService;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.quartz.spi.JobFactory;

@ProxyGen
public interface QuartzService {
    String SERVICE_ADDRESS = "io.piveau.scheduling.quartz.service";

    static QuartzService create(JobFactory jobFactory, LauncherService launcherService, Handler<AsyncResult<QuartzService>> readyHandler) {
        return new QuartzServiceImpl(jobFactory, launcherService, readyHandler);
    }

    static QuartzService createProxy(Vertx vertx, String address) {
        return new QuartzServiceVertxEBProxy(vertx, address);
    }

    @Fluent
    QuartzService listTriggers(Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    QuartzService getTriggers(String pipeId, Handler<AsyncResult<JsonArray>> handler);

    @Fluent
    QuartzService createOrUpdateTrigger(String pipeId, JsonArray triggerArray, Handler<AsyncResult<String>> handler);

    @Fluent
    QuartzService deleteTriggers(String pipeId, Handler<AsyncResult<Void>> handler);

    @Fluent
    QuartzService setTriggerStatus(String pipeId, String triggerId, String status, Handler<AsyncResult<String>> handler);

}
