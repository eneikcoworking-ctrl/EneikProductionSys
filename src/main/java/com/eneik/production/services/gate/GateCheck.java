package com.eneik.production.services.gate;

import com.eneik.production.models.persistence.TaskEntity;
import java.util.List;

public interface GateCheck {
    GateResult check(TaskEntity task);
}
