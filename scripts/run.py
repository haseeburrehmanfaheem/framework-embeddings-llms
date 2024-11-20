# IMPORTS

from __future__ import absolute_import, division, print_function

import argparse
import copy
import glob
import json
import jsonlines
import logging
import matplotlib.pyplot as plt
import multiprocessing
import numpy as np
import os
import pandas as pd
import pickle
import random
import re
import shutil
import string
import torch
from collections import Counter
from imblearn.over_sampling import SMOTE
from keras.datasets import imdb
from keras.layers import LSTM, Activation, Dropout, Dense, Input, Embedding
from keras.models import Model
from keras.preprocessing.text import Tokenizer
from keras_preprocessing.sequence import pad_sequences
from sklearn.ensemble import RandomForestClassifier
from sklearn.feature_extraction.text import TfidfVectorizer, CountVectorizer
from sklearn.metrics import (accuracy_score, precision_score, recall_score, 
                             f1_score, confusion_matrix, roc_curve, auc, 
                             classification_report)
from sklearn.model_selection import train_test_split, cross_val_score, learning_curve
from sklearn.preprocessing import LabelEncoder, LabelBinarizer
from tabulate import tabulate
from torch.autograd import Variable
from torch.nn import CrossEntropyLoss, MSELoss
import torch.nn.functional as F
from torch.utils.data import (DataLoader, Dataset, SequentialSampler, 
                              RandomSampler, TensorDataset, DistributedSampler)
from tqdm import tqdm, trange
from transformers import (AutoTokenizer, AutoModel, AdamW, 
                          get_linear_schedule_with_warmup, 
                          RobertaConfig, RobertaForSequenceClassification, RobertaTokenizer)
import joblib
import keras
import shap
import tensorflow as tf
import torch.nn as nn


# Get the GPU device name.
device_name = tf.test.gpu_device_name()

# The device name should look like the following:
if device_name == '/device:GPU:0':
    print('Found GPU at: {}'.format(device_name))
else:
    pass

# Model Initialization
model_name = 'microsoft/codebert-base'
model = AutoModel.from_pretrained(model_name, max_length=512)
tokenizer = AutoTokenizer.from_pretrained(model_name)


#################
# Pre-Processing
#################

def split_camel_case(text):
    inputs = tokenizer(text, return_tensors='pt', padding=True, max_length=512, truncation=True)
    words = re.findall(r'[A-Za-z][a-z]+', string)
    words = [w.lower() for w in words]
    return ' '.join(words)

def splitMethodName(x):
    return x.split('.')[-1].split('(')[0]

def convert_examples_to_features(js,tokenizer):
    #source
    code=' '.join(js['code'].split())
    code_tokens=tokenizer.tokenize(code)[:256-2]
    source_tokens =[tokenizer.cls_token]+code_tokens+[tokenizer.sep_token]
    source_ids =  tokenizer.convert_tokens_to_ids(source_tokens)
    padding_length = 256 - len(source_ids)
    source_ids+=[tokenizer.pad_token_id]*padding_length
    return InputFeatures(source_tokens,source_ids,js['label'])

# Vocabulary Set
all_words = set()
# Encode the natural language code using CodeBERT
def encode_code(text):
    # text = text.replace('/', ' ').replace('.', ' ')
    inputs = tokenizer(text, return_tensors='pt', padding=True, max_length=512, truncation=True)
    input_ids = inputs['input_ids'][0].tolist()  # Get the input IDs as a list
    words = tokenizer.convert_ids_to_tokens(input_ids)  # Convert input IDs to words

    ans = []

    for word in words:
      if word not in ['<s>', '</s>']:
        all_words.add(word)
        ans.append(word)
    return ans

def apply_parallel(df, func, num_processes):
    with mp.Pool(processes=num_processes) as pool:
        result = pool.map(func, df)
    return result


#################
# CLASSES
#################

class Model(nn.Module):

    def __init__(self, encoder,config,tokenizer):
        super(Model, self).__init__()
        self.encoder = encoder
        self.config=config
        self.tokenizer=tokenizer


    def forward(self, input_ids=None,labels=None):
        logits=self.encoder(input_ids,attention_mask=input_ids.ne(1))[0]
        prob=torch.softmax(logits,-1)
        if labels is not None:
            loss_fct = nn.CrossEntropyLoss(ignore_index=-1)
            loss = loss_fct(logits,labels)
            return loss,prob
        else:
            return prob

class InputFeatures(object):
    """A single training/test features for a example."""
    def __init__(self,
                 input_tokens,
                 input_ids,
                 label,

    ):
        self.input_tokens = input_tokens
        self.input_ids = input_ids
        self.label=label

class TextDatasetSmote(Dataset):
    def __init__(self, tokenizer, file_path=None):
        self.examples = []
        minor = []
        minC = 0
        major = []
        majC = 0
        with open(file_path) as f:
            for line in f:
                js=json.loads(line.strip())
                if js["label"] == 0:
                    minor.append(js)
                else:
                    major.append(js)

        minC = len(minor)
        majC = len(major)

        newMin = []

        while minC < majC:
            r1, r2 = random.sample(minor, 2)
            mergeCode = r1['code'] + '; ' + r2['code']
            label = r1['label']
            mergedJS = {'code': mergeCode, 'label': label}
            newMin.append(mergedJS)
            minC += 1

        for i in minor:
            self.examples.append(convert_examples_to_features(i,tokenizer))
        for i in major:
            self.examples.append(convert_examples_to_features(i,tokenizer))
        for i in newMin:
            self.examples.append(convert_examples_to_features(i,tokenizer))

        if 'train' in file_path:
            print("Label: 0", minC)
            print("Label: 1", majC)
            for idx, example in enumerate(self.examples[:3]):
                    print("*** Example ***")
                    print("label: {}".format(example.label))
                    print("input_tokens: {}".format([x.replace('\u0120','_') for x in example.input_tokens]))
                    print("input_ids: {}".format(' '.join(map(str, example.input_ids))))

    def __len__(self):
        return len(self.examples)

    def __getitem__(self, i):
        return torch.tensor(self.examples[i].input_ids),torch.tensor(self.examples[i].label)

class TextDataset(Dataset):
    def __init__(self, tokenizer, file_path=None):
        self.examples = []
        with open(file_path) as f:
            for line in f:
                js=json.loads(line.strip())
                self.examples.append(convert_examples_to_features(js,tokenizer))
        if 'train' in file_path:
            for idx, example in enumerate(self.examples[:3]):
                    print("*** Example ***")
                    print("label: {}".format(example.label))
                    print("input_tokens: {}".format([x.replace('\u0120','_') for x in example.input_tokens]))
                    print("input_ids: {}".format(' '.join(map(str, example.input_ids))))

    def __len__(self):
        return len(self.examples)

    def __getitem__(self, i):
        return torch.tensor(self.examples[i].input_ids),torch.tensor(self.examples[i].label)
    
#################
# TRAINING PARAMETERS
#################

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
n_gpu = torch.cuda.device_count()

rom_name = 'aosp'
model_name = rom_name + '_model.bin'
# dir_output = './output_xiaomi_civi13/'
dir_output = '../model/'
res_output = '../results/'

data_train = '../data/' + rom_name + '.jsonl'
data_validate = f"../data/{rom_name}_validate.jsonl"

num_epochs = 10


##################
# TRAINING PROCESS
##################

def set_seed(seed=42):
    random.seed(seed)
    os.environ['PYHTONHASHSEED'] = str(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    torch.cuda.manual_seed(seed)
    torch.backends.cudnn.deterministic = True

def train(train_dataset, model, tokenizer):
    """ Train the model """
    train_sampler = RandomSampler(train_dataset)

    train_dataloader = DataLoader(train_dataset, sampler=train_sampler,
                                  batch_size=8,num_workers=4,pin_memory=True)


    # Prepare optimizer and schedule (linear warmup and decay)
    no_decay = ['bias', 'LayerNorm.weight']
    optimizer_grouped_parameters = [
        {'params': [p for n, p in model.named_parameters() if not any(nd in n for nd in no_decay)],
         'weight_decay': 0.0},
        {'params': [p for n, p in model.named_parameters() if any(nd in n for nd in no_decay)], 'weight_decay': 0.0}
    ]
    optimizer = AdamW(optimizer_grouped_parameters, lr=2e-5, eps=1e-8)
    max_steps = len(train_dataloader) * 5
    scheduler = get_linear_schedule_with_warmup(optimizer, num_warmup_steps=max_steps*0.1,
                                                num_training_steps=max_steps)

    # Train!
    print("***** Running training *****")
    print("  Num examples = %d", len(train_dataset))
    print("  Num Epochs = %d", num_epochs)
    print("  batch size = %d", 8)
    print("  Total optimization steps = %d", max_steps)
    best_acc=0.0
    model.zero_grad()

    for idx in range(num_epochs):

        patience = 3
        early_stopping_counter = 0  # Counter for tracking epochs without improvement
        stop_training = False

        bar = tqdm(train_dataloader,total=len(train_dataloader))
        losses=[]
        for step, batch in enumerate(bar):
            inputs = batch[0].to(device)
            labels=batch[1].to(device)
            model.train()

            loss,logits = model(inputs,labels)

            if n_gpu > 1:
                loss = loss.mean()  # mean() to average on multi-gpu parallel training

            loss.backward()
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)

            losses.append(loss.item())
            bar.set_description("epoch {} loss {}".format(idx,round(np.mean(losses),3)))
            optimizer.step()
            optimizer.zero_grad()
            scheduler.step()

        results = evaluate(model, tokenizer)
        for key, value in results.items():
            print("  %s = %s", key, round(value,4))

        # Save model checkpoint
        if results['eval_acc']>best_acc:
            best_acc=results['eval_acc']
            print("  "+"*"*20)
            print("  Best acc:%s",round(best_acc,4))
            print("  "+"*"*20)

            checkpoint_prefix = 'checkpoint-best-acc'
            output_dir = os.path.join(dir_output, '{}'.format(checkpoint_prefix))
            if not os.path.exists(output_dir):
                os.makedirs(output_dir)
            model_to_save = model.module if hasattr(model,'module') else model
            output_dir = os.path.join(output_dir, '{}'.format(model_name))
            torch.save(model_to_save.state_dict(), output_dir)
            print("Saving model checkpoint to %s", output_dir)
        else:
            early_stopping_counter += 1  # Increment the counter
            # Check if the counter has reached the patience limit
            if early_stopping_counter >= patience:
                stop_training = True  # Set the flag to stop training
                break

    if stop_training:
        print("Early stopping! No improvement in validation accuracy for {} epochs.".format(patience))

def evaluate(model, tokenizer):
    eval_output_dir = res_output

    eval_dataset = TextDataset(tokenizer, data_validate)

    if not os.path.exists(eval_output_dir):
        os.makedirs(eval_output_dir)


    eval_sampler = SequentialSampler(eval_dataset)
    eval_dataloader = DataLoader(eval_dataset, sampler=eval_sampler, batch_size=16,num_workers=4,pin_memory=True)

    # Eval!
    print("***** Running evaluation *****")
    print("  Num examples = %d", len(eval_dataset))
    print("  Batch size = %d", 16)
    eval_loss = 0.0
    nb_eval_steps = 0
    model.eval()
    logits=[]
    labels=[]
    for batch in eval_dataloader:
        inputs = batch[0].to(device)
        label=batch[1].to(device)
        with torch.no_grad():
            lm_loss,logit = model(inputs,label)
            eval_loss += lm_loss.mean().item()
            logits.append(logit.cpu().numpy())
            labels.append(label.cpu().numpy())
        nb_eval_steps += 1
    logits=np.concatenate(logits,0)
    labels=np.concatenate(labels,0)
    preds=logits.argmax(-1)
    eval_acc=np.mean(labels==preds)
    eval_loss = eval_loss / nb_eval_steps
    perplexity = torch.tensor(eval_loss)


  # Compute evaluation metrics
    accuracy = accuracy_score(labels, preds)
    precision = precision_score(labels, preds)
    recall = recall_score(labels, preds)
    f1 = f1_score(labels, preds)
    confusion_mat = confusion_matrix(labels, preds)

    # Calculate false positives and false negatives
    false_positives = confusion_mat.sum(axis=0) - np.diag(confusion_mat)
    false_negatives = confusion_mat.sum(axis=1) - np.diag(confusion_mat)

    # Compute ROC curve and AUC
    fpr, tpr, thresholds = roc_curve(labels, preds)
    roc_auc = auc(fpr, tpr)

    # Print evaluation metrics
    print("Eval Loss: %.4f" % (eval_loss / nb_eval_steps))
    print("Accuracy: %.4f" % accuracy)
    print("Precision: %.4f" % precision)
    print("Recall: %.4f" % recall)
    print("F1 Score: %.4f" % f1)
    print("Confusion Matrix:\n", confusion_mat)
    print("False Positives:", false_positives)
    print("False Negatives:", false_negatives)
    print("ROC AUC: %.4f" % roc_auc)

    # Plot ROC curve
    plt.figure()
    plt.plot(fpr, tpr, color='darkorange', lw=2, label='ROC curve (AUC = %.4f)' % roc_auc)
    plt.plot([0, 1], [0, 1], color='navy', lw=2, linestyle='--')
    plt.xlim([0.0, 1.0])
    plt.ylim([0.0, 1.05])
    plt.xlabel('False Positive Rate')
    plt.ylabel('True Positive Rate')
    plt.title('Receiver Operating Characteristic')
    plt.legend(loc="lower right")
    plt.savefig(dir_output + '/roc_auc_curve.png')

    result = {
        "eval_loss": float(eval_loss / nb_eval_steps),
        "eval_accuracy": accuracy,
        "perplexity": float(perplexity),
        "eval_precision": precision,
        "eval_recall": recall,
        "eval_f1_score": f1,
        "confusion Matrix": confusion_mat,
        "false_positives": false_positives,
        "false_negatives": false_negatives,
        "roc_auc": roc_auc
    }

    with open(f"{eval_output_dir}/results.txt", w) as f:
        for key in sorted(result.keys()):
            f.write("  %s = %s", key, str(round(result[key],4)))


    # result = {
    #     "eval_acc":round(eval_acc,4),
    # }

    return result

import sys, getopt

def run(argv):
  
  try:
    opts, args = getopt.getopt(argv, "h", ["train=", "test="])
  except getopt.GetoptError:
    sys.exit(2)

  do_train = False
  do_eval = False

  for opt, arg in opts:
    if opt == '-h':
      print('Example: python run.py --train=<train_path> --test=<test_path>')
      sys.exit()
    elif opt == "--train":
      data_train = arg
      do_train = True
    elif opt == "--test":
      data_validate = arg
      do_eval = True

  if not (do_train or do_eval):
      sys.exit(2)

  device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
  n_gpu = torch.cuda.device_count()

  device = device

  print("device: %s, n_gpu: %s", device, n_gpu)

  # Set seed
  set_seed(42)

  config = RobertaConfig.from_pretrained("microsoft/codebert-base")
  config.num_labels=2
  tokenizer = RobertaTokenizer.from_pretrained("microsoft/codebert-base")
  model = RobertaForSequenceClassification.from_pretrained("microsoft/codebert-base",config=config)

  model=Model(model,config,tokenizer)

  # Loading Pre-Trained Model.
  checkpoint_prefix = 'checkpoint-best-acc'
  output_dir = os.path.join(dir_output, '{}'.format(checkpoint_prefix))
  try:
    # model.load_state_dict(torch.load(output_dir, map_location=torch.device('cpu')))
    model.load_state_dict(torch.load(output_dir))
  except:
    pass

  # multi-gpu training (should be after apex fp16 initialization)
  model.to(device)
  if n_gpu > 1:
      model = torch.nn.DataParallel(model)

  # Training
  if do_train:
      train_dataset = TextDataset(tokenizer, data_train)
      train(train_dataset, model, tokenizer)

  # Evaluation
#   results = {}
  if do_eval:
      checkpoint_prefix = 'checkpoint-best-acc/' + model_name
      output_dir = os.path.join(dir_output, '{}'.format(checkpoint_prefix))
      model.load_state_dict(torch.load(output_dir))
      model.to(device)
      result=evaluate(model, tokenizer)
      print("***** Eval results *****")
      for key in sorted(result.keys()):
          print("  %s = %s", key, str(round(result[key],4)))
      

  return model

if __name__ == "__main__":
    model = run(sys.argv[1:])

















