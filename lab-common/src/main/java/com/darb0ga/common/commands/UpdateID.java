package com.darb0ga.common.commands;


import com.darb0ga.common.collection.LabWork;
import com.darb0ga.common.collection.Models.AskLabWork;
import com.darb0ga.common.exceptions.IllegalParamException;
import com.darb0ga.common.exceptions.NoSuchIDException;
import com.darb0ga.common.managers.CollectionManager;
import com.darb0ga.common.managers.DBManager;
import com.darb0ga.common.util.Reply;


import java.util.Scanner;

/**
 * Команда 'update_id'. Обновляет значение элемента коллекции, id которого равен заданному
 *
 * @author darya
 */

public class UpdateID extends Command {
    public UpdateID() {
        super("update_id", "обновить значение элемента коллекции, id которого равен заданному", true);
    }

    @Override
    public Reply execute(String args, Scanner scan, boolean isFile, DBManager manager) throws IllegalParamException, NoSuchIDException {
        Reply reply = new Reply();
        try {
            LabWork lab = manager.findById(Integer.parseInt(args.trim()));
            if (lab != null) {
                manager.updateById(new AskLabWork().build(scan, isFile), Integer.parseInt(args.trim()));
                reply.addResponse("Внесены изменения в обьект с id " + args.trim());
            }
        } catch (NumberFormatException e) {
            reply.addResponse("Ошибка при работе с id");
        }
        return reply;
    }
}
