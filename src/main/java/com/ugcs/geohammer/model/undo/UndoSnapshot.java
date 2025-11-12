package com.ugcs.geohammer.model.undo;

import com.ugcs.geohammer.model.Model;

public interface UndoSnapshot {

    void restore(Model model);
}
