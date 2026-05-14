package dev.autotix.infrastructure.persistence.sla;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * MyBatis Plus entity for sla_policy table.
 * One row per TicketPriority (UNIQUE on priority column).
 */
@Data
@TableName("sla_policy")
public class SlaPolicyEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** Priority level: LOW / NORMAL / HIGH / URGENT */
    private String priority;

    @TableField("first_response_minutes")
    private Integer firstResponseMinutes;

    @TableField("resolution_minutes")
    private Integer resolutionMinutes;

    private Boolean enabled;

    @TableField("created_at")
    private Instant createdAt;

    @TableField("updated_at")
    private Instant updatedAt;
}
