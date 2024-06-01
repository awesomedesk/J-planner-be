package com.awesomedesk.j_planner.common.domain;

import jakarta.persistence.Embeddable;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Embeddable
public class DateDto {

     private LocalDateTime startDateTime;
     private LocalDateTime endDateTime;

     public DateDto(LocalDateTime startDateTime, LocalDateTime endDateTime) {
          this.startDateTime = startDateTime;
          this.endDateTime = endDateTime;
     }

}
