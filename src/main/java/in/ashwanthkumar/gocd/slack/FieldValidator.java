package in.ashwanthkumar.gocd.slack;

import java.util.Map;

public interface FieldValidator {
    public void validate(Map<String, Object> fieldValidation);
}
