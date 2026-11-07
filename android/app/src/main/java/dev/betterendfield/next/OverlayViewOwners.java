package dev.betterendfield.next;

import android.view.View;
import androidx.lifecycle.ViewTreeLifecycleOwner;
import androidx.lifecycle.ViewTreeViewModelStoreOwner;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.savedstate.SavedStateRegistryOwner;
import androidx.savedstate.ViewTreeSavedStateRegistryOwner;

/** AndroidX's Java owner setters remain callable when Kotlin hides their static facade. */
final class OverlayViewOwners {
    private OverlayViewOwners() { }

    static void set(View view, LifecycleOwner lifecycle, ViewModelStoreOwner models,
            SavedStateRegistryOwner state) {
        ViewTreeLifecycleOwner.set(view, lifecycle);
        ViewTreeViewModelStoreOwner.set(view, models);
        ViewTreeSavedStateRegistryOwner.set(view, state);
    }
}
