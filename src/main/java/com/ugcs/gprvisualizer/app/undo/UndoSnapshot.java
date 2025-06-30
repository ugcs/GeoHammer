package com.ugcs.gprvisualizer.app.undo;

import com.ugcs.gprvisualizer.gpr.Model;

public interface UndoSnapshot {

    void restore(Model model);
}
