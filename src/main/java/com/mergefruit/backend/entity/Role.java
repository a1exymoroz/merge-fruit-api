package com.mergefruit.backend.entity;

/*
 Learning Notes

 What: Enum representing user roles for authorization.
 Why: Role-based access control (RBAC) — e.g. only ADMIN can delete any score.

 Try yourself:
 - Add a MODERATOR role and restrict DELETE to ADMIN + MODERATOR.

 Common mistake:
 - Storing roles as free-text strings in the DB without validation.
*/
public enum Role {
    USER,
    ADMIN
}
