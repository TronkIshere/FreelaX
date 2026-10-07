package com.marketplace.backend.service;

import com.marketplace.backend.exception.ApplicationException;
import com.marketplace.backend.exception.ErrorCode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class JobSkills {
    private JobSkills() {}

    public static List<String> normalize(List<String> input) {
        if (input == null) return new ArrayList<>();
        if (input.size() > 10) throw new ApplicationException(ErrorCode.INVALID_DATA);
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String value : input) {
            if (value == null) throw new ApplicationException(ErrorCode.INVALID_DATA);
            String skill = value.trim();
            if (skill.length() < 2 || skill.length() > 40 || !seen.add(skill.toLowerCase(Locale.ROOT))) {
                throw new ApplicationException(ErrorCode.INVALID_DATA);
            }
            result.add(skill);
        }
        return result;
    }
}
