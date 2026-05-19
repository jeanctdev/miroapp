package com.miroapp.auth.exception;

import com.miroapp.common.exception.ResourceNotFoundException;

// =====================================================================
// UserNotFoundException — usuario no encontrado
// El mensaje viene del Service usando MessageSource
// =====================================================================
public class UserNotFoundException extends ResourceNotFoundException {

  public UserNotFoundException(String message) {
    super(message);
  }
}