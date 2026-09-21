package com.resume2portifolio.domain;

import java.util.List;

public record ResumeDocument(String schemaVersion, Basics basics, String summary,
                             List<Experience> experience, List<Education> education,
                             List<String> skills, List<Language> languages, List<Project> projects) {
    public record Basics(String name, String headline, String email, String phone, String location, List<Link> links) {}
    public record Link(String label, String url) {}
    public record Experience(String company, String role, String startDate, String endDate,
                             List<String> description, List<String> technologies) {}
    public record Education(String institution, String degree, String startDate, String endDate) {}
    public record Language(String name, String proficiency) {}
    public record Project(String name, String description, List<String> technologies, String url) {}
}
