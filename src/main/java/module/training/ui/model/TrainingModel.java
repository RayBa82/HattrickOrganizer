package module.training.ui.model;

import core.db.DBManager;
import core.gui.comp.table.PlayersTable;
import core.model.HOVerwaltung;
import core.training.FutureTrainingManager;
import core.training.FutureTrainingPlan;
import core.training.TrainingPerWeek;
import core.training.WeeklyTrainingType;
import module.training.PastTrainingManager;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.List;

public class TrainingModel implements PropertyChangeListener {
    private List<TrainingPerWeek> futureTrainings;
	private PastTrainingManager skillupManager;
	private FutureTrainingManager futureTrainingManager;
	private final List<ModelChangeListener> listeners = new ArrayList<>();

	public TrainingModel(){
        HOVerwaltung.instance().addPropertyChangeListener(this);
	}

	public void propertyChange(PropertyChangeEvent evt) {
		futureTrainings = null;
	}

	public PastTrainingManager getSkillupManager() {
        var selectedPlayer = PlayersTable.Companion.getSelectedPlayer();
		if (this.skillupManager == null || this.skillupManager.getPlayer() != selectedPlayer) {
			this.skillupManager = new PastTrainingManager(selectedPlayer);
		}
		return this.skillupManager;
	}

	public List<TrainingPerWeek> getFutureTrainings() {
		if (futureTrainings == null) {
			// rolled forward to the coming weeks and stored, sized to the user's preference
			futureTrainings = FutureTrainingPlan.current();
		}
		return futureTrainings;
	}

	public void updateFutureTrainings(List<TrainingPerWeek> trainings) {
		for (TrainingPerWeek training : trainings) {
			DBManager.instance().saveFutureTraining(training);
		}
		futureTrainings = null; //force reload
		fireModelChanged(ModelChange.FUTURE_TRAINING);
	}

	public void saveFutureTraining(TrainingPerWeek training) {
		DBManager.instance().saveFutureTraining(training);
		fireModelChanged(ModelChange.FUTURE_TRAINING);
	}

	public void addModelChangeListener(ModelChangeListener listener) {
		if (!this.listeners.contains(listener)) {
			this.listeners.add(listener);
		}
	}

	public FutureTrainingManager getFutureTrainingManager() {
		var selectedPlayer = PlayersTable.Companion.getSelectedPlayer();
		if (this.futureTrainingManager == null || this.futureTrainingManager.getPlayer() != selectedPlayer) {
			// gets the list of future trainings
			List<TrainingPerWeek> trainings = getFutureTrainings();
			// instantiate a future train manager to calculate the previsions */
			this.futureTrainingManager = new FutureTrainingManager(selectedPlayer, trainings);
		}
		return this.futureTrainingManager;
	}

	public void resetFutureTrainings() {
        this.futureTrainings = null;
        this.futureTrainingManager = null;
		fireModelChanged(ModelChange.FUTURE_TRAINING);
	}

	public void fireModelChanged(ModelChange change) {
		for (int i = listeners.size() - 1; i >= 0; i--) {
			listeners.get(i).modelChanged(change);
		}
	}

	// full training: Primary Training Position && ! set pieces || Bonus Position (&& set pieces)
	// partial training: Secondary Training Position || primary Training Position && set pieces)
	public boolean isPartialTrainingAvailable(int[] weeks) {
		for ( var w : weeks){
			var t = getFutureTrainings().get(w);
			if ( isPartialTrainingAvailable(t)) return true;
		}
		return false;
	}

    public boolean isOsmosisTrainingAvailable(int[] weeks) {
		for ( var w : weeks){
			var t = getFutureTrainings().get(w);
			if ( isOsmosisTrainingAvailable(t)) return true;
		}
		return false;
    }

	public boolean isPartialTrainingAvailable() {
		for ( var t : getFutureTrainings()){
			if ( isPartialTrainingAvailable(t)) return true;
		}
		return false;
	}

	private boolean isPartialTrainingAvailable(TrainingPerWeek t) {
		var tt = WeeklyTrainingType.instance(t.getTrainingType());
		if ( tt != null ) return tt.getTrainingSkillPartlyTrainingPositions().length > 0 ||
				tt.getTrainingSkillBonusPositions().length > 0;
		return false;
	}

	public boolean isOsmosisTrainingAvailable() {
		for ( var t : getFutureTrainings()){
			if ( isOsmosisTrainingAvailable(t)) return true;
		}
		return false;
	}

	private boolean isOsmosisTrainingAvailable(TrainingPerWeek t) {
		var tt = WeeklyTrainingType.instance(t.getTrainingType());
		if ( tt != null ) return tt.getTrainingSkillOsmosisTrainingPositions().length > 0;
		return false;
	}
}
