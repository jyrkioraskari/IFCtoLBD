package org.linkedbuildingdata.ifc2lbd.core.utils;

import java.util.Optional;

public class ChangeableOptonal<type> {
    private Optional<type> value = Optional.empty();
    
    public void setValue(type value) {
        this.value = Optional.of(value);
    }

    public type get() {
        return value.get();
    }

    
    public Optional<type> getValue() {
        return value;
    }

    public void overwriteIfPresent(Optional<type> value) {
        if(value.isPresent())
          this.value = value;
    }

    public boolean isPresent()
    {
        return value.isPresent();
    }
    
}
