package pl.edu.ur.pz.clinicapp.dialogs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RegisterDialogTest {

    @Test
    void emailValidator() {
        assertTrue(RegisterDialog.emailValidator("akowalski@gmail.com"));
        assertFalse(RegisterDialog.emailValidator("@gmail.com"));
        assertFalse(RegisterDialog.emailValidator("akowalski@gmailcom"));
    }

    @Test
    void PESELValidator() {
        assertTrue(RegisterDialog.PESELValidator("11111111111"));
        assertFalse(RegisterDialog.PESELValidator("11111111"));
        assertFalse(RegisterDialog.PESELValidator("1111ab11111"));
    }

    @Test
    void phoneValidator() {
        assertTrue(RegisterDialog.phoneValidator("123456789"));
        assertFalse(RegisterDialog.phoneValidator("1236789"));
        assertFalse(RegisterDialog.phoneValidator("123vv6789"));
    }

    @Test
    void postCodeValidator() {
        assertTrue(RegisterDialog.postCodeValidator("12-384"));
        assertFalse(RegisterDialog.postCodeValidator("12345"));
        assertFalse(RegisterDialog.postCodeValidator("12-g60"));
    }

    @Test
    void nameSurnameValidator() {
        assertTrue(RegisterDialog.nameSurnameSpecializationValidator("Grzegorz"));
        assertTrue(RegisterDialog.nameSurnameSpecializationValidator("Brzęczyszczykiewicz"));
        assertFalse(RegisterDialog.nameSurnameSpecializationValidator("Adam22"));
        assertFalse(RegisterDialog.nameSurnameSpecializationValidator("Adam_"));
    }
}