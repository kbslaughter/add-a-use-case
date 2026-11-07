package team.projectpulse.student;

import team.projectpulse.section.Section;
import team.projectpulse.team.Team;
import team.projectpulse.user.PeerEvaluationUser;
import jakarta.persistence.*;
import org.hibernate.annotations.DynamicUpdate;

// An UPDATE writes only the changed columns, so an edit that loaded the student before a deactivation committed cannot
// write enabled back to true.
@Entity
@DynamicUpdate
public class Student extends PeerEvaluationUser {

    @ManyToOne(optional = false)
    private Section section;
    @ManyToOne
    private Team team;


    public Student() {
    }

    public Student(String username, String firstName, String lastName, String email, String password, boolean enabled, String roles) {
        super(username, firstName, lastName, email, password, enabled, roles);
    }

    public Section getSection() {
        return section;
    }

    public void setSection(Section section) {
        this.section = section;
    }

    public Team getTeam() {
        return team;
    }

    public void setTeam(Team team) {
        this.team = team;
    }

}
