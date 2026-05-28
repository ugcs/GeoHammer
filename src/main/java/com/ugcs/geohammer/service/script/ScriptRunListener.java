package com.ugcs.geohammer.service.script;

public interface ScriptRunListener {

    void onRunStarted();

    void onRunFinished();

    void onSuccess(ScriptMetadata metadata);

    void onError(ScriptMetadata metadata, Exception e, String scriptOutput);

    boolean confirmReinstallDependencies(String moduleName);
}
