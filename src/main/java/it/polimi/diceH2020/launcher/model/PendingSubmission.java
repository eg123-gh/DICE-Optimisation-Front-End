/*
Copyright 2017 Eugenio Gianniti

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package it.polimi.diceH2020.launcher.model;

import it.polimi.diceH2020.SPACE4Cloud.shared.settings.Scenario;
import lombok.Data;


import javax.persistence.*;
import java.util.List;

@Data
@Entity
public class PendingSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(cascade = {CascadeType.PERSIST, CascadeType.REFRESH, CascadeType.MERGE})
    private Scenario scenario;

    @Column(length = 10000)
    private String instanceData;

    @Column(length = 20000)
    @ElementCollection
    private List<String> paths;
}
