package io.piveau.scheduling.launcher;

import io.piveau.pipe.ModelKt;
import io.piveau.pipe.PipeLauncher;
import io.piveau.pipe.PiveauCluster;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

import java.util.List;
import java.util.stream.Collectors;

public class LauncherServiceImpl implements LauncherService {

    private PipeLauncher launcher;

    LauncherServiceImpl(PiveauCluster cluster, Handler<AsyncResult<LauncherService>> readyHandler) {
        launcher = cluster.pipeLauncher();
        readyHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public LauncherService launch(String pipeName, JsonObject configs, Handler<AsyncResult<Void>> handler) {
        if (launcher.isPipeAvailable(pipeName)) {
            launcher.runPipe(pipeName, configs);
            handler.handle(Future.succeededFuture());
        } else {
            handler.handle(Future.failedFuture("Pipe not available."));
        }
        return this;
    }

    @Override
    public LauncherService isPipeAvailable(String pipeName, Handler<AsyncResult<Boolean>> handler) {
        handler.handle(Future.succeededFuture(launcher.isPipeAvailable(pipeName)));
        return this;
    }

    @Override
    public LauncherService getPipe(String pipeName, Handler<AsyncResult<JsonObject>> handler) {
        if (launcher.isPipeAvailable(pipeName)) {
            handler.handle(Future.succeededFuture(new JsonObject(ModelKt.prettyPrint(launcher.getPipe(pipeName)))));
        } else {
            handler.handle(Future.failedFuture("Pipe not available."));
        }
        return this;
    }

    @Override
    public LauncherService availablePipes(Handler<AsyncResult<List<JsonObject>>> handler) {
        List<JsonObject> list = launcher.availablePipes().stream().map(p -> new JsonObject(ModelKt.prettyPrint(p))).collect(Collectors.toList());
        handler.handle(Future.succeededFuture(list));
        return this;
    }

}
