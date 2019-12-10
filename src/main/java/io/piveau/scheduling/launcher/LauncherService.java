package io.piveau.scheduling.launcher;

import io.piveau.pipe.Pipe;
import io.piveau.pipe.PiveauCluster;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.ProxyGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.List;

@ProxyGen
public interface LauncherService {
    String SERVICE_ADDRESS = "io.piveau.scheduling.launcher.service";

    static LauncherService create(PiveauCluster cluster, Handler<AsyncResult<LauncherService>> readyHandler) {
        return new LauncherServiceImpl(cluster, readyHandler);
    }

    static LauncherService createProxy(Vertx vertx, String address) {
        return new LauncherServiceVertxEBProxy(vertx, address);
    }

    @Fluent
    LauncherService launch(String pipeName, JsonObject configs, Handler<AsyncResult<Void>> handler);

    @Fluent
    LauncherService isPipeAvailable(String pipeName, Handler<AsyncResult<Boolean>> handler);

    @Fluent
    LauncherService getPipe(String pipeName, Handler<AsyncResult<JsonObject>> handler);

    @Fluent
    LauncherService availablePipes(Handler<AsyncResult<List<JsonObject>>> handler);

}
