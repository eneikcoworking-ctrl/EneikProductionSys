package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;

public interface GateCheck {
    GateResult check(TaskEntity task);
}
