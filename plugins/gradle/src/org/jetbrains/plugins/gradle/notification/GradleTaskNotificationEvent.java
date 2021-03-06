package org.jetbrains.plugins.gradle.notification;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.internal.task.GradleTaskId;

import java.io.Serializable;

/**
 * Encapsulates information about processing state change of the {@link #getId() target task}.
 * 
 * @author Denis Zhdanov
 * @since 11/10/11 9:19 AM
 */
public class GradleTaskNotificationEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  private final GradleTaskId myId;
  private final String myDescription;

  public GradleTaskNotificationEvent(@NotNull GradleTaskId id, @NotNull String description) {
    myId = id;
    myDescription = description;
  }
  
  @NotNull
  public GradleTaskId getId() {
    return myId;
  }

  @NotNull
  public String getDescription() {
    return myDescription;
  }

  @Override
  public int hashCode() {
    return 31 * myDescription.hashCode() + myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleTaskNotificationEvent that = (GradleTaskNotificationEvent)o;
    return myId.equals(that.myId) && myDescription.equals(that.myDescription);
  }

  @Override
  public String toString() {
    return myId + "-" + myDescription;
  }
}
