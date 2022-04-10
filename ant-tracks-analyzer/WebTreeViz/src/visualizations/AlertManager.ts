import * as d3 from "d3";

export default class AlertManager {

    private static selectionAlert: d3.Selection<HTMLDivElement, void, HTMLElement, void>;
    private static selectionAlertOldNode: d3.Selection<HTMLElement, void, HTMLElement, void>;
    private static selectionAlertNewNode: d3.Selection<HTMLElement, void, HTMLElement, void>;
    private static selectionAlertCloseBtn: d3.Selection<HTMLButtonElement, void, HTMLElement, void>;
    private static selectionAlertTimer: d3.Timer | null = null;

    private constructor() {
    }

    public static init() {
        AlertManager.selectionAlert = d3.select<HTMLDivElement, void>('#selectionAlert');
        AlertManager.selectionAlertOldNode = d3.select<HTMLElement, void>('#selectionAlert .oldNode');
        AlertManager.selectionAlertNewNode = d3.select<HTMLElement, void>('#selectionAlert .newNode');
        AlertManager.selectionAlertCloseBtn = d3.select<HTMLButtonElement, void>('#selectionAlertCloseBtn')
                                                .on('click', () => AlertManager.hideAlert());
    }

    public static showAlert(oldNodeidString: string, newNodeidString: string) {
        AlertManager.selectionAlertOldNode.text(oldNodeidString);
        AlertManager.selectionAlertNewNode.text(newNodeidString);
        AlertManager.selectionAlert.classed('d-none', false);
        if (AlertManager.selectionAlertTimer != null) {
            AlertManager.selectionAlertTimer.stop();
        }
        AlertManager.selectionAlertTimer = d3.timer(_ => {
            AlertManager.hideAlert();
        }, 6000);
    }

    public static hideAlert() {
        AlertManager.selectionAlert.classed('d-none', true);
        if (AlertManager.selectionAlertTimer != null) {
            AlertManager.selectionAlertTimer.stop();
            AlertManager.selectionAlertTimer = null;
        }
    }

}